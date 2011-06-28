var GaeMiniProfiler = {

    init: function(requestId, fShowImmediately) {
        // Fetch profile results for any ajax calls
        // (see http://code.google.com/p/mvc-mini-profiler/source/browse/MvcMiniProfiler/UI/Includes.js)
        $(document).ajaxComplete(function (e, xhr, settings) {
            if (xhr) {
                var requestId = xhr.getResponseHeader('X-MiniProfiler-Id');
                if (requestId) {
                    GaeMiniProfiler.fetch(requestId);
                }
            }
        });
	
	$().ready(function() { GaeMiniProfiler.fetch(requestId, fShowImmediately) });
    },

    fetch: function(requestId, fShowImmediately) {
        $.get("/_webjure_profiler/request", { "id": requestId },
              function(data) {

                  $('body').append(data);
              });
    },

    finishFetch: function(data, fShowImmediately) {
        var jCorner = this.renderCorner(data);

        if (!jCorner.data("attached")) {
            $('body')
                .append(jCorner)
                .click(function(e) { return GaeMiniProfiler.collapse(e); });
            jCorner
                .data("attached", true);
        }

        if (fShowImmediately)
            jCorner.find(".entry").first().click();
    },

    collapse: function(e) {
        if ($(".g-m-p").is(":visible")) {
            $(".g-m-p").slideUp("fast");
            $(".g-m-p-corner").slideDown("fast")
                .find(".expanded").removeClass("expanded");
            return false;
        }

        return true;
    },

    expand: function(elEntry, data) {
        var jPopup = $(".g-m-p");
    
        if (jPopup.length)
            jPopup.remove();
        else
            $(document).keyup(function(e) { if (e.which == 27) GaeMiniProfiler.collapse() });

        jPopup = this.renderPopup(data);
        $('body').append(jPopup);

        var jCorner = $(".g-m-p-corner");
        jCorner.find(".expanded").removeClass("expanded");
        $(elEntry).addClass("expanded");

        jPopup
            .find(".profile-link")
                .click(function() { GaeMiniProfiler.toggleSection(this, ".profiler-details"); return false; }).end()
            .find(".rpc-link")
                .click(function() { GaeMiniProfiler.toggleSection(this, ".rpc-details"); return false; }).end()
            .find(".callers-link")
                .click(function() { $(this).parents("td").find(".callers").slideToggle("fast"); return false; }).end()
            .click(function(e) { e.stopPropagation(); })
            .css("left", jCorner.offset().left + jCorner.width() + 18)
            .slideDown("fast");
    },

    toggleSection: function(elLink, selector) {

        var fWasVisible = $(selector).is(":visible");

        $(".expand").removeClass("expanded");
        $(".details:visible").slideUp(50)

        if (!fWasVisible) {
            $(elLink).parents(".expand").addClass("expanded");
            $(selector).slideDown("fast", function() {
                if (!GaeMiniProfiler.toggleSection["called_" + selector]) {
                    $(selector + " table").tablesorter();
                    GaeMiniProfiler.toggleSection["called_" + selector] = true;
                }
            });
        }
    },

    renderPopup: function(data) {
        return $("#profilerTemplate").tmpl(data);
    },

    renderCorner: function(data) {
        if (data && data.profiler_results) {
            var jCorner = $(".g-m-p-corner");

            var fFirst = false;
            if (!jCorner.length) {
                jCorner = $("#profilerCornerTemplate").tmpl();
                fFirst = true;
            }

            return jCorner.append(
                    $("#profilerCornerEntryTemplate")
                        .tmpl(data)
                        .addClass(fFirst ? "" : "ajax")
                        .click(function() { GaeMiniProfiler.expand(this, data); return false; })
                    );
        }
        return null;
    }
};

