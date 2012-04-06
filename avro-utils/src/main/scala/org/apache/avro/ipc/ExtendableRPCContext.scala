package org.apache.avro.ipc

import scala.collection.JavaConversions._
import java.nio.ByteBuffer

class ExtendableRPCContext extends RPCContext {
  override def requestCallMeta = super.requestCallMeta()
  def requestCallMeta_= (newmeta:Map[String,ByteBuffer]) = super.setRequestCallMeta(newmeta)
  def setRequestCallMeta1 (newmeta:Map[String,ByteBuffer]) = super.setRequestCallMeta(newmeta)
  def requestHandshakeMeta_= (newmeta:Map[String,ByteBuffer]) = super.setRequestHandshakeMeta(newmeta)
  def requestPayload_= (payload:List[ByteBuffer]) = super.setRequestPayload(payload)
  
  def responseCallMeta_= (newmeta:Map[String,ByteBuffer]) = super.setResponseCallMeta(newmeta)
  override def responseCallMeta = super.responseCallMeta()
  
  def response_= (response:Any) = super.setResponse(response)
  def setResponse1(response:Any) = super.setResponse(response)
  def error_= (error:Exception) = super.setError(error)
  def setError1(error:Exception) = super.setError(error)
}