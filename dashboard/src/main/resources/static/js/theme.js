// Applies a saved theme override before first paint (this script is loaded first in <head>,
// deliberately not deferred/async) to avoid a flash of the wrong theme. No framework, no build
// step -- matches the rest of this project's zero-external-dependency stance. Same mechanism as
// ci-triage-dashboard's theme.js, own storage key so the two don't clash if ever viewed on the
// same origin/path prefix.
(function () {
  var STORAGE_KEY = "loadtest-theme";

  function apply(theme) {
    if (theme === "light" || theme === "dark") {
      document.documentElement.setAttribute("data-theme", theme);
    } else {
      document.documentElement.removeAttribute("data-theme");
    }
  }

  apply(localStorage.getItem(STORAGE_KEY));

  window.addEventListener("DOMContentLoaded", function () {
    var btn = document.getElementById("theme-toggle-btn");
    if (!btn) return;

    function current() {
      var explicit = document.documentElement.getAttribute("data-theme");
      if (explicit) return explicit;
      return window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
    }

    function updateIcon() {
      var isDark = current() === "dark";
      btn.querySelector(".icon-sun").style.display = isDark ? "none" : "block";
      btn.querySelector(".icon-moon").style.display = isDark ? "block" : "none";
    }

    updateIcon();
    btn.addEventListener("click", function () {
      var next = current() === "dark" ? "light" : "dark";
      apply(next);
      localStorage.setItem(STORAGE_KEY, next);
      updateIcon();
    });
  });
})();
