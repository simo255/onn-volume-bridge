package io.github.simo255.onnvolumebridge;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
/** Real loopback HTTP fixture, with no server library in the app or test dependencies. */
final class FakeHttpServer implements AutoCloseable {
 interface Handler { Response handle(Request request) throws Exception; }
 static final class Request {final String method,path,body;final Map<String,String> headers;Request(String m,String p,String b,Map<String,String> h){method=m;path=p;body=b;headers=h;}}
 static final class Response {
  final int status;final byte[] body;final String location;
  Response(int status,byte[] body,String location){this.status=status;this.body=body;this.location=location;}
  static Response ok(String body){return new Response(200,body.getBytes(StandardCharsets.UTF_8),null);}
 }
 private final ServerSocket listener;private final Thread thread;private volatile Throwable failure;
 FakeHttpServer(Handler handler)throws IOException {
  listener=new ServerSocket(0,20,InetAddress.getByName("127.0.0.1"));
  thread=new Thread(()->{while(!listener.isClosed()){
   try(Socket socket=listener.accept()){
    socket.setSoTimeout(3000);InputStream input=socket.getInputStream();ByteArrayOutputStream head=new ByteArrayOutputStream();int tail=0;
    while(head.size()<16384){int b=input.read();if(b<0)throw new EOFException();head.write(b);tail=(tail<<8)|b;if(tail==0x0d0a0d0a)break;}
    String[] lines=head.toString("US-ASCII").split("\r\n");String[] first=lines[0].split(" ");Map<String,String> headers=new HashMap<>();
    for(int i=1;i<lines.length;i++){int colon=lines[i].indexOf(':');if(colon>0)headers.put(lines[i].substring(0,colon).toLowerCase(Locale.ROOT),lines[i].substring(colon+1).trim());}
    int length=Integer.parseInt(headers.getOrDefault("content-length","0"));byte[] body=new byte[length];for(int i=0;i<length;){int n=input.read(body,i,length-i);if(n<0)throw new EOFException();i+=n;}
    Response response=handler.handle(new Request(first[0],first[1],new String(body,StandardCharsets.UTF_8),headers));
    String reply="HTTP/1.1 "+response.status+" Test\r\nContent-Length: "+response.body.length+"\r\nConnection: close\r\n"+(response.location==null?"":"Location: "+response.location+"\r\n")+"\r\n";
    socket.getOutputStream().write(reply.getBytes(StandardCharsets.US_ASCII));socket.getOutputStream().write(response.body);socket.getOutputStream().flush();
   }catch(SocketException e){if(!listener.isClosed())failure=e;}catch(Throwable e){failure=e;}
  }},"FakeTV-http");thread.setDaemon(true);thread.start();
 }
 int port(){return listener.getLocalPort();}
 @Override public void close()throws Exception{listener.close();thread.join(2000);if(failure!=null)throw new AssertionError("Fake TV server failed",failure);}
}
