
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Named;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author avald
 */
@Path("chat")
public class ChatServer {
    protected Message first;
    protected Message last;
    protected int max_msgs = 10;
    private static final Logger LOGGER = Logger.getLogger(ChatServer.class.getName());
    
    protected LinkedHashMap<String, Message> messages = new LinkedHashMap<String, Message>(){
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Message> eldest){
            boolean remove = size() > max_msgs;
            if(remove) first = eldest.getValue().next;
            return remove;
        }
        
    };
    protected AtomicLong counter = new AtomicLong(0);
    LinkedList<AsyncResponse> listeners = new LinkedList<AsyncResponse>();
    ExecutorService writer = Executors.newSingleThreadExecutor();
    
    @Context
    protected UriInfo uriInfo;
    
    @POST
    @Consumes("text/plain")
    public void post(final String text){
        LOGGER.log(Level.INFO,"IN--------------------------------------------------");
        final UriBuilder base = uriInfo.getBaseUriBuilder();
        writer.submit(new Runnable() {
            @Override
            public void run() {
                synchronized(messages){
                    Message message = new Message(text, Long.toString(counter.incrementAndGet()));
                    
                    if(messages.size() ==0) first = message;
                    else last.next = message;
                    
                    messages.put(message.getFrom() , message);
                    last = message;
                    
                    for(AsyncResponse async: listeners){
                        try{
                            send(base, async, message);
                        }catch(Exception e){
                            e.printStackTrace();
                        }
                    }
                    listeners.clear();
                }
            }
        });
    }
    
    @GET
    public void recieve(@QueryParam("current") String id, @Suspended AsyncResponse async){
        LOGGER.log(Level.INFO,"IN--------------------------------------------------");
        final UriBuilder base = uriInfo.getBaseUriBuilder();
        Message message = null;
        first = new Message("first", Long.toString(counter.incrementAndGet()));
        synchronized(messages){
            Message current = messages.get(id);
            if(current == null) message = first;
            else message = first;
            //else message = current.next;
            
            if(message == null) queue(async);
        }
        
        if(message != null) send(base, async, message);
    }
    
    protected void queue(AsyncResponse async){
        listeners.add(async);
    }
    
    protected void send(UriBuilder base, AsyncResponse async, Message message){
        Response response = Response.ok(message.message(), 
                MediaType.TEXT_PLAIN_TYPE).header("Location", base+"chat?current=" + message.getFrom()).build();
        async.resume(null);
    }
    
}
