package main

import (
	"flag"
	"html/template"
  "io"
	"log"
	"net/http"

	"code.google.com/p/go.net/websocket"
	"github.com/nu7hatch/gouuid"
)

var hub = newWsServiceHub()
var clients = map[*uuid.UUID]*WsClient{}
var listen = flag.String("listen", ":8080", "")
var handlers = map[string]WsEventHandlerFunc{
	"register": WsHandleRegister,
	"message": WsHandleMessage,
}

var templates = template.Must(template.New("index").Parse(HTML_TEMPLATE_INDEX))

func init() {
	http.HandleFunc("/", func(w http.ResponseWriter, req *http.Request) {
		if err := templates.ExecuteTemplate(w, "index", nil); err != nil {
			http.Error(w, err.Error(), http.StatusInternalServerError)
		}
	})
  http.Handle("/ws", websocket.Server{Handler: HandleWS})

	flag.Parse()
}

func main() {
	log.Fatal(http.ListenAndServe(*listen, nil))
}

func WsHandleRegister(handler *WsEventHandler, event WsEvent) error {
  if username, test := event.Data["username"]; test && nil != username {
    handler.client.Id = username.(string);
    handler.events <- WsEvent{
      "registration",
      WsEventData{
        "username": handler.client.Id,
      },
    }
    return nil
  }
  return _error("Invalid registration data; 'username' missing");
}

func WsHandleMessage(handler *WsEventHandler, event WsEvent) error {
  for _, peer := range hub.Handlers {
    event.Data["username"] = handler.client.Id
    peer.events <- event
  }
  return nil
}

type _error string

func (e _error) Error() string {
  return string(e)
}

// -----------------------------------------------------------------------------

func HandleWS(ws *websocket.Conn) {
	remoteAddr := ws.Request().RemoteAddr
	log.Println("Connection from", remoteAddr, "opened")

	handler := NewWsEventHandler(newWsClient(ws), handlers)
	hub.AddHandler(handler)

	defer func() {
		log.Println("Connection from", remoteAddr, "closed")
		hub.RemoveHandler(handler)
	}()

	go handler.Writer()
	handler.Reader()
}

//
type WsClient struct {
	Id string
	Conn *websocket.Conn
}

func newWsClient(c *websocket.Conn) *WsClient {
	return &WsClient{Conn:c}
}

//
type WsEventData map[string]interface{}

type WsEvent struct {
	Event string      `json:"event"`
	Data  WsEventData `json:"data"`
}

//
type WsEventHandlerNotFound string

func (e WsEventHandlerNotFound) Error() string {
	return "Event Handler '" + string(e) + "' not found!"
}

func newWsEventHandlerNotFound(e string) WsEventHandlerNotFound {
	return WsEventHandlerNotFound(e)
}

type WsEventHandlerFunc func(*WsEventHandler, WsEvent) error

type WsEventHandler struct {
  id       *uuid.UUID
	client   *WsClient
	done     chan bool
	events   chan WsEvent
	handlers map[string]WsEventHandlerFunc
}

func NewWsEventHandler(c *WsClient, h map[string]WsEventHandlerFunc) *WsEventHandler {
	return &WsEventHandler{
		client: c,
		done: make(chan bool),
		events: make(chan WsEvent, 1),
		handlers: h,
	}
}

func (H *WsEventHandler) Handle(e WsEvent) error {
	if h, t := H.handlers[e.Event]; t {
		return h(H, e)
	}
	return newWsEventHandlerNotFound(e.Event)
}

func (H *WsEventHandler) Reader() {
	for {
		var ev WsEvent
		if err := websocket.JSON.Receive(H.client.Conn, &ev); nil != err {
			log.Printf("Reader[%s]: %s", H.client.Id, err)
			if io.EOF == err {
				H.done <- true
				return
			}
		}
		log.Printf("Reader[%s]: %#v", H.client.Id, ev)
		if err := H.Handle(ev); nil != err {
			log.Printf("Reader[%s]: %s", H.client.Id, err)
		}
	}
}

func (H *WsEventHandler) Writer() {
	for {
		select {
		case e := <-H.events:
			log.Printf("Writer[%s]: %#v", H.client.Id, e)
			if err := websocket.JSON.Send(H.client.Conn, e); nil != err {
				log.Printf("Writer[%s]: %s", H.client.Id, err)
			}
		case <-H.done:
			log.Printf("Writer[%s]: exit", H.client.Id)
			return
		}
	}
}

//
type WsServiceHub struct {
	Handlers map[*uuid.UUID]*WsEventHandler
}

func newWsServiceHub() WsServiceHub {
	return WsServiceHub{map[*uuid.UUID]*WsEventHandler{}}
}

func (s *WsServiceHub) AddHandler(h *WsEventHandler) error {
	if id, err := uuid.NewV4(); nil == err {
		if _, t := s.Handlers[id]; !t {
			h.client.Id = id.String()
			s.Handlers[id] = h
      h.id = id
		}
	} else {
		return err
	}
	return nil
}

func (s *WsServiceHub) RemoveHandler(h *WsEventHandler) {
	if _, t := s.Handlers[h.id]; t {
		delete(s.Handlers, h.id)
	}
}

// -----------------------------------------------------------------------------

const HTML_TEMPLATE_INDEX = `
<!DOCTYPE html>
<html lang="en">
<head>
	<meta charset="utf-8"/>
	<meta name="viewport" content="initial-scale=1.0, minimum-scale=1.0, maximum-scale=1.0, width=device-width"/>
	<title>• chat •</title>
  <style type="text/css">
    html { height: 100% }
    body { height: 100%; margin: 0 auto; padding: 0 }
    #chat { height: 100%; margin: 5px; padding: 5px }

    submit {
    	width: 96%;
    	height: 30px;
    	margin: 6px auto;
    	display: block;
    }

    ul {
    	list-style: none;
    	font-size: 15px;
    }

    li:nth-child(odd) {
    	background: #cdcdcd;
    }

    li {
    	padding: 5px 10px;
    	margin: 10px 0 0;
    	font-family: 'lucida grande',tahoma,verdana,arial,sans-serif;
    }
  </style>
	<script type="text/javascript">
    function WsEventDispatcher(url) {
      var self = this;
      var d = function(event, message) {
        var fn = self._fn[event];
        if (typeof fn != "undefined") {
          for (var i in fn) {
            fn[i](message);
          }
        }
      };

      this._fn = {};
      this._ws = new WebSocket(url);

      this._ws.onclose = function() { d("close", null); };
      this._ws.onerror = function() { d("error", null); };
      this._ws.onopen = function() { d("open", null); };
      this._ws.onmessage = function(event) {
        var o = JSON.parse(event.data);
        d(o.event, o.data);
      };
    }

    WsEventDispatcher.prototype.bind = function(name, fn) {
      this._fn[name] = this._fn[name] || [];
      this._fn[name].push(fn);
      return this;
    };

    WsEventDispatcher.prototype.send = function(name, data) {
      this._ws.send(JSON.stringify({event:name,data:data}));
      return this;
    };

    WsEventDispatcher.prototype.close = function() {
      this._ws.close();
    }

    var dispatcher;
    var chat;

    function init() {
      dispatcher = new WsEventDispatcher("ws://localhost:8080/ws");
      chat = document.getElementById("messages");

      dispatcher.bind("open", function() {
        console.log("WebSocket connection open");
        register();
      });

      dispatcher.bind("close", function() {
        console.log("WebSocket connection closed");
      });

      dispatcher.bind("message", function(event) {
        console.log("Message: " + JSON.stringify(event));
        show(event);
      });

      dispatcher.bind("registration", function(event) {
        if (event.username == undefined) {
          console.log("Invalid registration response: " + JSON.stringify(event));
        }
        console.log("username='"+event.username+"'");
        dispatcher.send("message", {message:"[Connected]"});
      });
    }

    function register() {
      var username = "";
      do {
        username = window.prompt("Enter your username");
      } while("" == username);
      dispatcher.send("register", {username:username});
    }

    function send() {
      console.log("send: " + document.newmessage.message.value);
      dispatcher.send("message", {
        message: document.newmessage.message.value,
      });
      document.newmessage.message.value = "";
      return false;
    }

    function show(event) {

      var username = document.createElement("span");
      var message = document.createElement("span");
      var li = document.createElement("li");

      username.innerHTML = event.username + ": ";
      username.style.marginRight = "5px";
      username.style.fontStyle = "italic";
      username.style.fontWeight = "bold";

      message.innerHTML = event.message;
      message.style.wordWrap = "break-word";

      li.appendChild(username);
      li.appendChild(message);

      chat.appendChild(li);
    }

    window.addEventListener("load", init);
  </script>
</head>

<body>
  <div id="chat">

    <div>
      <ul id="messages" />
    </div>

    <div id="newmessageform">
      <form name="newmessage" action="#" onsubmit="return send();">
        <input type="text" name="message" value="" style="width: 90%">
        <input type="submit" value="send" style="width: 5%">
      </form>
    </div>

  </div>
</body>

</html>
`
