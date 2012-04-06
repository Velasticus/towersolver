package org.apache.avro.ipc.specific

import scala.collection.JavaConversions._
import org.apache.avro.Protocol
import org.apache.avro.generic.GenericRecord
import org.apache.avro.Schema
import org.apache.avro.specific.SpecificData
import java.lang.reflect.InvocationTargetException
import org.apache.avro.AvroRuntimeException
import java.nio.ByteBuffer
import org.apache.avro.ipc.Transceiver
import org.apache.avro.io.DecoderFactory
import org.apache.avro.util.ByteBufferInputStream
import org.apache.avro.util.ByteBufferOutputStream
import org.apache.avro.io.EncoderFactory
import org.apache.avro.ipc.RPCContext
import org.apache.avro.specific.SpecificDatumReader
import org.apache.avro.io.Decoder
import org.apache.avro.ipc.HandshakeRequest
import org.apache.avro.io.Encoder
import org.apache.avro.specific.SpecificDatumWriter
import java.util.concurrent.ConcurrentHashMap
import org.apache.avro.ipc.HandshakeResponse
import org.apache.avro.ipc.MD5
import org.apache.avro.ipc.HandshakeMatch
import com.weiglewilczek.slf4s.Logging
import org.apache.avro.util.Utf8
import org.apache.avro.UnresolvedUnionException
import org.apache.avro.generic.GenericDatumWriter
import org.apache.avro.generic.GenericDatumReader
import org.apache.avro.ipc.ExtendableRPCContext

class SpecificContextResponder(protected val protocol:Protocol,impl:Any, protected val data : SpecificData ) extends SpecificResponder(protocol,impl,data) with Logging {
  def this(protocol:Protocol,impl:Any) = this(protocol,impl,SpecificData.get)
  def this(iface:Class[_],impl:Any) = this(SpecificData.get.getProtocol(iface),impl)
  
  override def respond(buffers:java.util.List[ByteBuffer],connection:Transceiver) : java.util.List[ByteBuffer] = respond(null,buffers.toList,connection)
  def respond(ctx:Any,buffers:List[ByteBuffer]) : java.util.List[ByteBuffer] = respond(ctx,buffers,null)
  
  def respond(ctx:Any,buffers:List[ByteBuffer],connection:Transceiver) : java.util.List[ByteBuffer] = {
    val in = DecoderFactory.get().binaryDecoder(new ByteBufferInputStream(buffers), null)
    var bbo = new ByteBufferOutputStream
    var out = EncoderFactory.get().binaryEncoder(bbo, null)
    var error : Exception = null
    var context = new ExtendableRPCContext
    var payload : List[ByteBuffer] = null
    var handshakeBuf : List[ByteBuffer] = null
    val wasConnected = connection != null && connection.isConnected
    
    try {
      val remote = handshake(in, out, connection)
      out.flush
      if (remote==null)
        return bbo.getBufferList.toList
        
      handshakeBuf = bbo.getBufferList.toList
      
      // read request using remote protocol specification
      context.setRequestCallMeta1(SpecificContextResponder.META_READER.read(null, in))
      val messageName = in.readString(null).toString
      val rm = remote.getMessages().get(messageName)
      if (rm == null)
        throw new AvroRuntimeException("No such remote message: "+messageName);
      val m = getLocal().getMessages().get(messageName);
      if (m == null)
        throw new AvroRuntimeException("No message named "+messageName+" in "+getLocal());

      val request = readRequest(rm.getRequest(), m.getRequest(), in)
      context.setMessage(rm);
      rpcMetaPlugins.foreach(_.serverReceiveRequest(context))

      // create response using local protocol specification
      if ((m.isOneWay() != rm.isOneWay()) && wasConnected)
        throw new AvroRuntimeException("Not both one-way: "+messageName)

      var response : Object = null;
      try {
        REMOTE.set(remote)
        response = respond(ctx, m, request)
        context.setResponse1(response)
      } catch {
        case e:Exception => {
          error = e
          context.setError1(e)
          logger.warn("user error", e)
        } case e => throw e
      } finally {
        REMOTE.set(null)
      }

      if (m.isOneWay() && wasConnected)           // no response data
        return null

      out.writeBoolean(error != null)
      if (error == null)
        writeResponse(m.getResponse(), response, out)
      else
        try {
          writeError(m.getErrors(), error, out)
        } catch {
          case e:UnresolvedUnionException => throw error
        }

    } catch {
      case e:Exception => {
        logger.warn("system error", e);
        context.setError1(e)
        bbo = new ByteBufferOutputStream();
        out = EncoderFactory.get().binaryEncoder(bbo, null);
        out.writeBoolean(true);
        writeError(Protocol.SYSTEM_ERRORS, new Utf8(e.toString()), out);
        if (null == handshakeBuf)
          handshakeBuf = new ByteBufferOutputStream().getBufferList().toList

      }
      case _ =>
    }
    
    out.flush
    payload = bbo.getBufferList.toList
    
    // Grab meta-data from plugins
    context.setResponsePayload(payload);
    rpcMetaPlugins.foreach( _.serverSendResponse(context) )
    SpecificContextResponder.META_WRITER.write(context.responseCallMeta.toMap, out);
    out.flush();
    // Prepend handshake and append payload
    bbo.prepend(handshakeBuf);
    bbo.append(payload);

    return bbo.getBufferList;
  }
  
  
  override def respond(msg:Protocol#Message,request:Any) = respond(null,msg,request)
  
  def respond(ctx:Any,msg:Protocol#Message,request:Any) : Object = {
    val numParams = msg.getRequest.getFields.size
    val params = new Array[ Object ](numParams + 1)
    val paramTypes = new Array[Class[_]](numParams + 1)
    
    params(0) = ctx.asInstanceOf[Object]
    paramTypes(0) = classOf[Object]
    
    val req = request.asInstanceOf[GenericRecord]
    
    try {
      var i = 1
      msg.getRequest.getFields.foreach { case (param) => {
        params(i) = req.get(param.name)
        paramTypes(i) = data.getClass(param.schema)
        i+=1
      }}
      
      val method = impl.getClass.getMethod(msg.getName, paramTypes:_*)
      method.setAccessible(true)
      return method.invoke(impl,params:_*)
    } catch {
      case e:InvocationTargetException => e.getTargetException match {
        case e1:RuntimeException => throw e1
        case e1 => throw new RuntimeException(e1) 
      }
      case e:NoSuchElementException => throw new AvroRuntimeException(e)
      case e:IllegalAccessException => throw new AvroRuntimeException(e)
    }
    
  }
  
  protected val protocols = new ConcurrentHashMap[MD5,Protocol]()
  protected val localHash = new MD5()
  localHash.bytes(protocol.getMD5)
  protocols.put(localHash,protocol)
  protected val handshakeWriter = new SpecificDatumWriter[HandshakeResponse](classOf[HandshakeResponse]);
  protected val handshakeReader = new SpecificDatumReader[HandshakeRequest](classOf[HandshakeRequest]);

  protected def handshake(in:Decoder,out:Encoder,connection:Transceiver) : Protocol = {
    if (connection != null && connection.isConnected)
      return connection.getRemote
    val request = handshakeReader.read(null, in)
    var remote = protocols.get(request.getClientHash)
    if ( remote == null && request.getClientProtocol != null ) {
      remote = Protocol.parse(request.getClientProtocol)
      protocols.put(request.getClientHash,remote)
    }
    
    val response = new HandshakeResponse
    if (remote == null)
      response.setMatch(HandshakeMatch.NONE)
    else if (localHash.equals(request.getServerHash))
      response.setMatch(HandshakeMatch.BOTH)
    else
      response.setMatch(HandshakeMatch.CLIENT)
      
    if (response.getMatch != HandshakeMatch.BOTH) {
      response.setServerProtocol(protocol.toString)
      response.setServerHash(localHash)
    }
    
    val context = new RPCContext
    context.setHandshakeRequest(request)
    context.setHandshakeResponse(response)
    rpcMetaPlugins.foreach(_.serverConnecting(context))
    
    handshakeWriter.write(response,out)
    
    if (connection!=null && response.getMatch != HandshakeMatch.NONE)
      connection.setRemote(remote)
      
    remote
  }
  
  def REMOTE = SpecificContextResponder.REMOTE
  
}

object SpecificContextResponder {
  private final val REMOTE = new ThreadLocal[Protocol]();

  private final val META = Schema.createMap(Schema.create(Schema.Type.BYTES));
  private final val META_READER = new GenericDatumReader[Map[String,ByteBuffer]](META);
  private final val META_WRITER = new GenericDatumWriter[Map[String,ByteBuffer]](META);
}