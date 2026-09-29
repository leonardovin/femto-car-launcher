// Runtime polyfills for the Android 9 fork's WebView floor (chrome67, see
// build.target in vite.config.ts). The build lowers syntax only; these fill
// the post-Chromium-67 built-ins that maplibre-gl and the page code call
// unconditionally. The file is plain script text, never a module: the Vite
// plugin in vite.config.ts inlines it ahead of the page's module entry and
// prepends it to the classic MapLibre worker, so both realms see the same
// built-ins. Every fill is guarded, so a current WebView runs none of it.
// The top-level block keeps every helper binding out of the global scope.
{
    const root = typeof self === "undefined" ? window : self;
    const define = (target, name, value) => {
        if (target && !(name in target)) {
            Object.defineProperty(target, name, { value, writable: true, configurable: true });
        }
    };

    define(root, "globalThis", root);

    function at(index) {
        const length = this.length;
        const relative = Math.trunc(index) || 0;
        const k = relative >= 0 ? relative : length + relative;
        return k < 0 || k >= length ? undefined : this[k];
    }
    define(Array.prototype, "at", at);
    define(String.prototype, "at", at);
    if (typeof Int8Array !== "undefined") {
        define(Object.getPrototypeOf(Int8Array.prototype), "at", at);
    }

    const flatten = (source, level) =>
        source.reduce(
            (acc, item) =>
                Array.isArray(item) && level > 0
                    ? acc.concat(flatten(item, level - 1))
                    : acc.concat([item]),
            [],
        );
    define(Array.prototype, "flat", function flat(depth) {
        return flatten(this, depth === undefined ? 1 : Number(depth));
    });
    define(Array.prototype, "flatMap", function flatMap(callback, thisArg) {
        return this.map(callback, thisArg).flat(1);
    });

    define(String.prototype, "replaceAll", function replaceAll(search, replacement) {
        if (search instanceof RegExp) {
            if (!search.global) {
                throw new TypeError("replaceAll must be called with a global RegExp");
            }
            return this.replace(search, replacement);
        }
        const escaped = String(search).replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
        return this.replace(new RegExp(escaped, "g"), replacement);
    });

    define(Object, "hasOwn", (object, key) => Object.prototype.hasOwnProperty.call(object, key));

    define(Object, "fromEntries", (entries) => {
        const result = {};
        for (const [key, value] of entries) {
            result[key] = value;
        }
        return result;
    });

    define(Promise, "allSettled", (promises) =>
        Promise.all(
            Array.from(promises, (promise) =>
                Promise.resolve(promise).then(
                    (value) => ({ status: "fulfilled", value }),
                    (reason) => ({ status: "rejected", reason }),
                ),
            ),
        ),
    );

    // postMessage(message, { transfer }) (Chromium 71+): older engines accept
    // only a transfer sequence and throw "Iterator getter is not callable" on
    // the options dict. Probing is unreliable (MessagePort accepts the dict
    // before Worker does), so the dict is always unwrapped to the equivalent
    // transfer list, which every engine accepts with the same meaning.
    // Error and DOMException became structured-cloneable in Chromium 77;
    // MapLibre posts abort errors across the worker boundary. On a
    // DataCloneError the message is retried with every error replaced by a
    // plain { name, message, stack } object. Only plain objects and arrays are
    // walked, so buffers (and the transfer list) keep their identity.
    const isError = (value) =>
        value instanceof Error ||
        (typeof DOMException !== "undefined" && value instanceof DOMException);
    const withPlainErrors = (value) => {
        if (isError(value)) {
            return { name: value.name, message: value.message, stack: value.stack };
        }
        if (Array.isArray(value)) return value.map(withPlainErrors);
        if (value !== null && typeof value === "object") {
            const proto = Object.getPrototypeOf(value);
            if (proto !== Object.prototype && proto !== null) return value;
            const copy = {};
            for (const key of Object.keys(value)) copy[key] = withPlainErrors(value[key]);
            return copy;
        }
        return value;
    };
    const unwrapTransfer = (target) => {
        if (!target || typeof target.postMessage !== "function") return;
        const original = target.postMessage;
        target.postMessage = function postMessage(message, options) {
            const transfer =
                options !== null &&
                typeof options === "object" &&
                typeof options[Symbol.iterator] !== "function"
                    ? options.transfer || []
                    : options;
            const send = (payload) =>
                transfer === undefined
                    ? original.call(this, payload)
                    : original.call(this, payload, transfer);
            try {
                return send(message);
            } catch (error) {
                if (!error || error.name !== "DataCloneError") throw error;
                return send(withPlainErrors(message));
            }
        };
    };
    unwrapTransfer(typeof Worker === "undefined" ? undefined : Worker.prototype);
    unwrapTransfer(typeof MessagePort === "undefined" ? undefined : MessagePort.prototype);
    // Inside the MapLibre worker, postMessage lives on the global object itself
    // ([Global] interfaces hold their operations on the instance), not on
    // DedicatedWorkerGlobalScope.prototype. Never patch window.postMessage: its
    // second argument is a target origin.
    if (
        typeof DedicatedWorkerGlobalScope !== "undefined" &&
        root instanceof DedicatedWorkerGlobalScope
    ) {
        unwrapTransfer(root);
    }

    define(root, "queueMicrotask", (callback) => {
        Promise.resolve()
            .then(callback)
            .catch((error) =>
                setTimeout(() => {
                    throw error;
                }),
            );
    });
}
