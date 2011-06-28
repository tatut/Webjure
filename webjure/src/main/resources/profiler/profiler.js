var WebjureMiniProfiler = {

    init: function(requestId, fShowImmediately) {
        // Fetch profile results for any ajax calls
        // (see http://code.google.com/p/mvc-mini-profiler/source/browse/MvcMiniProfiler/UI/Includes.js)
        $(document).ajaxComplete(function (e, xhr, settings) {
            if (xhr) {
                var requestId = xhr.getResponseHeader('X-MiniProfiler-Id');
                if (requestId) {
                    WebjureMiniProfiler.fetch(requestId);
                }
            }
        });
	
	$().ready(function() { WebjureMiniProfiler.fetch(requestId, fShowImmediately) });
    },

    fetch: function(requestId, fShowImmediately) {
        $.get("/_webjure_profiler/request", { "id": requestId },
              function(data) {
                  $('body').append(data);
              });
    }
};

