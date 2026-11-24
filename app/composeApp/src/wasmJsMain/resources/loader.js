(function () {
    "use strict";

    var loader = document.getElementById("loader");
    var loaderText = document.getElementById("loader-text");
    var loaderSpinner = document.getElementById("loader-spinner");
    var reloadBtn = document.getElementById("loader-reload");
    if (!loader) return;

    function isMounted() {
        return !document.body.contains(loader);
    }

    if (reloadBtn) {
        reloadBtn.addEventListener("click", function () {
            window.location.reload();
        });
    }

    function showFailure(message) {
        if (isMounted()) return;
        if (loaderSpinner) loaderSpinner.hidden = true;
        if (loaderText) loaderText.textContent = message;
        if (reloadBtn) reloadBtn.hidden = false;
    }

    var slowTimer = window.setTimeout(function () {
        if (isMounted()) return;
        if (loaderText) {
            loaderText.textContent = "Baixando recursos do portal...";
        }
    }, 5000);

    var verySlowTimer = window.setTimeout(function () {
        if (isMounted()) return;
        if (loaderText) {
            loaderText.textContent = "A primeira carga pode levar alguns segundos...";
        }
        if (reloadBtn) reloadBtn.hidden = false;
    }, 15000);

    function cleanup() {
        window.clearTimeout(slowTimer);
        window.clearTimeout(verySlowTimer);
        window.removeEventListener("error", onError);
        window.removeEventListener("unhandledrejection", onRejection);
    }

    function onError() {
        if (isMounted()) {
            cleanup();
            return;
        }
        showFailure("Não foi possível carregar o UNICEP Portal Web. Tente recarregar.");
    }

    function onRejection() {
        if (isMounted()) {
            cleanup();
            return;
        }
        showFailure("Erro ao inicializar o aplicativo Web. Tente recarregar.");
    }

    window.addEventListener("error", onError);
    window.addEventListener("unhandledrejection", onRejection);

    if (typeof MutationObserver !== "undefined" && document.body) {
        var observer = new MutationObserver(function () {
            if (isMounted()) {
                cleanup();
                observer.disconnect();
            }
        });
        observer.observe(document.body, { childList: true });
    }
})();
