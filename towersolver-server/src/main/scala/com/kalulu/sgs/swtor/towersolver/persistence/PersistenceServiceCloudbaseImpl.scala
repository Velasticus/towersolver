package com.kalulu.sgs.swtor.towersolver.persistence

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URI
import java.util.Properties

import scala.annotation.serializable
import scala.collection.JavaConversions.iterableAsScalaIterable
import scala.collection.JavaConversions.seqAsJavaList

import org.apache.avro.io.DecoderFactory
import org.apache.avro.io.EncoderFactory
import org.apache.avro.specific.SpecificDatumWriter
import org.apache.avro.specific.SpecificDatumReader

import com.couchbase.client.protocol.views.Query
import com.couchbase.client.protocol.views.View
import com.couchbase.client.protocol.views.ViewRow
import com.couchbase.client.CouchbaseClient
import com.kalulu.sgs.swtor.towersolver.impl.ServerImpl
import com.kalulu.sgs.swtor.towersolver.protocol.server.SingleServer
import com.kalulu.sgs.swtor.towersolver.protocol.PersistentObjectType
import com.kalulu.sgs.swtor.towersolver.service.PersistenceServiceImpl
import com.kalulu.sgs.swtor.towersolver.traits.Player
import com.kalulu.sgs.swtor.towersolver.traits.ServerTrait
import com.sun.sgs.app.AppContext
import com.sun.sgs.app.ManagedReference
import com.sun.sgs.kernel.ComponentRegistry
import com.sun.sgs.services.app.AsyncTaskCallback
import com.sun.sgs.services.app.AsyncCallable
import com.sun.sgs.services.app.AsyncRunnable
import com.sun.sgs.services.app.TransactionRunner
import com.sun.sgs.service.TransactionProxy

import net.spy.memcached.transcoders.Transcoder
import net.spy.memcached.CachedData

class PersistenceServiceCloudbaseImpl(props:Properties,systemRegistry:ComponentRegistry,txnProxy:TransactionProxy)
  extends PersistenceServiceImpl(props,systemRegistry,txnProxy) with Serializable{

  // TODO Add this to configuration
  private val uris = new URI("http://localhost:8091/pools") :: Nil
  @transient
  private lazy val client = new CouchbaseClient(uris, "default", "")
  private val transcoder = ServerTranscoder()
  @transient
  private lazy val allServersView = client.getView("dev_servers","all_servers")
  
  def servers(resultHandler:ResultHandler[List[ServerTrait]]) = {
    taskService.startTask(GetServersOperation(client,allServersView,transcoder), new OperationResult(resultHandler));
  }
  
  def servers_= (servers: List[ServerTrait]) = {
    servers.foreach( server => client.set(server.name.replace(" ",""), Int.MaxValue, server, transcoder))
  }
  
  def getPlayer( id : Int ) : Player = {
    
    null
  }
  def getPlayer( email : String ) : Player = {
    
    null
  }
  
  def savePlayer(player:Player) = {
    
    null
  }
  
}

case class ServerTranscoder extends Transcoder[ServerTrait] {
  
  @transient
  lazy val writer = new SpecificDatumWriter[SingleServer](classOf[SingleServer])
  @transient
  lazy val reader = new SpecificDatumReader[SingleServer](classOf[SingleServer])
  
  def asyncDecode(data:CachedData) = {
    false
  }
  
  def encode(server:ServerTrait) : CachedData = {
    // If we can get to the underlying schema val then use it, otherwise build one
    val serverRecord = server match {
      case s : ServerImpl => s.server
      case s => SingleServer.newBuilder()
      .setName(server.name)
      .setType(server.serverType)
      .setRegion(server.region)
      .setContinent(server.continent)
      .setPopulation(server.population)
      .setStatus(server.status)
      .build()
    }
    val out = new ByteArrayOutputStream()
    val encoder = EncoderFactory.get.jsonEncoder(serverRecord.getSchema(),out)
    serverRecord.setObjectType(PersistentObjectType.SERVER)
    writer.write(serverRecord,encoder)
    encoder.flush
    new CachedData(0,out.toByteArray(),CachedData.MAX_SIZE)
  }
  
  def decode(data:CachedData) = {
    val stream = new ByteArrayInputStream(data.getData())
    val decoder = DecoderFactory.get().jsonDecoder(SingleServer.SCHEMA$,stream)
    val result = reader.read(null,decoder)
    ServerImpl(result)
  }
  
  def getMaxSize = {
    CachedData.MAX_SIZE
  }
}

@serializable
abstract sealed case class TransactionlessProcessor[T](client:CouchbaseClient) extends AsyncCallable[T] with AsyncRunnable {
  override def run(tranRunner:TransactionRunner) {
    val result = call(tranRunner)
    // TODO Log the result
  }
}

case class GetServersOperation(override val client:CouchbaseClient,private val view:View, private val transcoder:Transcoder[ServerTrait]) extends TransactionlessProcessor[List[ServerTrait]](client) {
  override def call(tranRunner:TransactionRunner) = {
    val query = new Query
    val resp = client.query(view,query)
    
    resp.map( (row : ViewRow) => {
      val data = new CachedData(0,row.getValue().getBytes,CachedData.MAX_SIZE)
      transcoder.decode(data)
    }).toList
  }
}

@serializable
sealed case class OperationResult[T](private val callback:ResultHandler[T]) extends AsyncTaskCallback[T] {
  var managedRef : ManagedReference[ResultHandler[T]] = null
  var ref : ResultHandler[T] = null
  callback match {
    case cb:ManagedReference[T] => managedRef = AppContext.getDataManager().createReference(callback)
    case cb:T => ref=cb
  }
  
  // This is transactional (i.e. runs within an SGS task
  def notifyResult(result:T) {
    if (managedRef == null)
      ref.result(result)
    else
      managedRef.get.result(result)
  }
  def notifyFailed(error:Throwable) {
    // TODO Log error
  }
  
}

trait ResultHandler[T] extends Serializable {
  def result(result:T) : Unit
}