import { readFileSync } from "node:fs";
import { resolve } from "node:path";
// vite-plus re-exports Vite's defineConfig with the Vite+ blocks (test, lint)
// typed; `vp dev` / `vp build` / `vp test` / `vp lint` / `vp check` all read
// this one file.
import { defineConfig } from "vite-plus";

// Android 9 fork: the built-ins legacy-polyfills.js fills must exist before any
// page module or MapLibre worker code runs. The page gets them as an inline
// classic script ahead of the module entry; the worker, a classic script Vite
// emits verbatim, gets them prepended to its source.
const legacyPolyfills = readFileSync(resolve(import.meta.dirname, "legacy-polyfills.js"), "utf8");
const legacyPolyfillsPlugin = {
    name: "femto-legacy-polyfills",
    transformIndexHtml: () => [
        { tag: "script", children: legacyPolyfills, injectTo: "head-prepend" as const },
    ],
    generateBundle(
        _options: unknown,
        bundle: Record<
            string,
            { fileName: string; type: string; code?: string; source?: string | Uint8Array }
        >,
    ) {
        for (const output of Object.values(bundle)) {
            if (!/maplibre-gl-worker.*\.m?js$/.test(output.fileName)) continue;
            if (output.type === "chunk") {
                output.code = [legacyPolyfills, output.code ?? ""].join("\n");
            } else if (typeof output.source === "string") {
                output.source = [legacyPolyfills, output.source].join("\n");
            } else if (output.source instanceof Uint8Array) {
                output.source = [legacyPolyfills, new TextDecoder().decode(output.source)].join(
                    "\n",
                );
            }
        }
    },
};

export default defineConfig({
    plugins: [legacyPolyfillsPlugin],
    // Relative asset URLs: the page is served from
    // https://appassets.androidplatform.net/assets/web/index.html, so absolute
    // "/assets/..." URLs would escape the web/ asset subtree.
    base: "./",
    build: {
        // Android 9 fork: the lowest WebView an Android 9 device realistically
        // ships is the Pie-era AOSP prebuilt (Chromium 66-69), which the Play
        // Store does not update on aftermarket units. chrome67 is the floor
        // the bundle can reach: maplibre-gl carries BigInt literals, which no
        // transform can lower below Chromium 67. Mirrored by
        // WEBMAP_CHROMIUM_FLOOR in the diagnostics (WebViewFactsCollector.kt).
        target: "chrome67",
        // dist/web/ mirrors the assets/web/ layout the Kotlin host expects; the
        // whole dist/ directory is wired into the Android assets source set.
        outDir: "dist/web",
        rollupOptions: {
            // One entry page for all backends; main.ts resolves ?backend= and
            // dynamic-imports the matching module, which Vite code-splits into
            // per-backend chunks.
            input: resolve(import.meta.dirname, "index.html"),
        },
    },
    test: {
        // style.ts is pure data transformation; no DOM environment needed.
        environment: "node",
        include: ["src/**/*.test.ts"],
    },
    lint: {
        // Oxlint settings live here, not in an .oxlintrc.json — `vp lint` /
        // `vp check` read only this block by default, and the Vite+ docs
        // recommend the single config home. Parity with the retired Biome
        // setup: the recommended preset maps to the correctness + suspicious
        // categories, noVar/useConst map to the eslint core rules below, and
        // the custom let ban moves from the Biome GritQL plugin (no-let.grit)
        // to the no-let.js Oxlint JS plugin (alpha API, dev-time only —
        // nothing from it ships).
        // legacy-polyfills.js is raw classic-script text the build inlines
        // (see legacyPolyfillsPlugin), not page module code.
        ignorePatterns: ["dist/**", "legacy-polyfills.js"],
        jsPlugins: ["./no-let.js"],
        categories: {
            correctness: "error",
            suspicious: "error",
        },
        // Type checking stays an explicit `tsc --noEmit` in the check script
        // (the same shape `vp migrate` generates). The alternative —
        // options.typeAware + options.typeCheck (tsgolint) — is coupled: the
        // type-aware RULE set comes with the type CHECK, and it outlaws the
        // deliberate boundary casts and defensive conversions the bridge code
        // is built on (e.g. no-unsafe-type-assertion on the window/UMD
        // accessors) — a stricter contract than the Biome-parity this
        // migration keeps. Enabling it is a deliberate future decision.
        rules: {
            "eslint/no-var": "error",
            "eslint/prefer-const": "error",
            "femto/no-let": "error",
        },
    },
});
