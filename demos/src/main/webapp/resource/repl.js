
var req;
function replCallback(txt) {
  if(req.readyState == 4) {
    if(req.status == 200) {
      if(req.responseText.length > 0) appendContent('=> '+req.responseText);
      readRepl();
    } else {
      alert('Unable to read repl: '+req.statusText);
    }
  }
} 

function readRepl() { 
  req = new XMLHttpRequest();
  req.onreadystatechange = replCallback;
  req.open('GET', '/webjure-demos/ajaxrepl-out', true);
  req.send(null);
} 
    
function appendContent(txt) {
  var elt = document.getElementById('replout');
  elt.innerHTML += txt + '\n';
  elt.scrollTop = elt.scrollHeight;
} 
         
function keyHandler(event) { if(event.keyCode == 13) write(); } 
function write() { 
  var elt = document.getElementById('replin');
  var r = new XMLHttpRequest();
  r.open('POST', '/webjure-demos/ajaxrepl-in', true);
  appendContent(elt.value);
  r.send(elt.value);
  elt.value = '';
}
window.onload = readRepl;


