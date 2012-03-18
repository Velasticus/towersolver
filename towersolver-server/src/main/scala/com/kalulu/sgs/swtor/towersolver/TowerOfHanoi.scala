package com.kalulu.sgs.swtor.towersolver

import scala.Double
import scala.actors.threadpool.Executor
import scala.collection.JavaConversions._
import scala.concurrent.TaskRunner
import scala.collection.mutable.Set
import scala.collection.mutable.ConcurrentMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.ConcurrentSkipListSet
import java.util.concurrent.ConcurrentHashMap
import scala.collection.mutable.ArrayBuilder
import scala.concurrent.ThreadPoolRunner
import scala.concurrent.TaskRunner
import scala.concurrent.TaskRunners
import scala.concurrent.FutureTaskRunner
import scala.concurrent.forkjoin.ForkJoinPool
import scala.collection.parallel.ForkJoinTasks
import scala.concurrent.forkjoin.ForkJoinTask
import scala.concurrent.forkjoin.RecursiveAction
import java.util.concurrent.TimeUnit
import org.jgrapht.UndirectedGraph
import org.jgrapht.graph.DefaultEdge
import org.jgrapht.graph.SimpleGraph
import org.jgrapht.alg.DijkstraShortestPath

case class InvalidMoveException extends RuntimeException

class TowerOfHanoiState(val pieces : Array[Int]) {
  lazy val hc = ( pieces.reverse.view.zipWithIndex :\ 0 ) { case ((value,exp),acc) => acc + value * Math.pow(3,exp).toInt }
  lazy val maxes = ( pieces.view.zipWithIndex :\ Array(0,0,0) ) { case ((value,idx),b) => {
    if ( b(value-1) < idx+1 )
      b(value-1) = idx+1
    b
  }}
  lazy val mins = ( pieces.view.zipWithIndex :\ Array(0,0,0) ) { case ((value,idx),b) => {
    if ( b(value-1) == 0 || b(value-1) > idx+1 )
      b(value-1) = idx+1
    b
  }}
  def isValidMove(from:Int,to:Int) = {
     mins(from) != 0 && ( mins(to) == 0 || mins(from) < mins(to) ) 
  }

  def move(from:Int,to:Int) = {
    if (!isValidMove(from,to))
      throw new InvalidMoveException
    val newPieces = pieces.clone()
    newPieces(mins(from)-1) = to+1
    new TowerOfHanoiState(newPieces)
  }
  override def equals(obj:Any) = {
    obj.isInstanceOf[TowerOfHanoiState] && obj.asInstanceOf[TowerOfHanoiState].hc == hc
  }
  override def hashCode = {
    hc
  }
  override def toString = "(" + ( pieces :\ "" ) ( _ + "," + _ ) + ")"
}


object TowerOfHanoi {
  def apply(n:Int) = {
    var n1 = n
    var pieces = ArrayBuilder.make[Int]
    while ( n1 > 0 ) {
      pieces += n1 % 3
      n1 /= 3
    }
  }
  def main(args:Array[String]) : Unit = {
    //println(hanoi(6,0,2,1))
    
    val state=new TowerOfHanoiState(Array(1,1,1,1,1,1,1,1))
    
    val nodes : ConcurrentMap[TowerOfHanoiState,Any] = new ConcurrentHashMap[TowerOfHanoiState,Any]()
    val edges : ConcurrentMap[Tuple2[TowerOfHanoiState,TowerOfHanoiState],Any] = new ConcurrentHashMap[Tuple2[TowerOfHanoiState,TowerOfHanoiState],Any]()
    val fj = new ForkJoinPool()

    fj.invoke(new HanoiMoves(state,nodes,edges))
    val graph = new SimpleGraph[TowerOfHanoiState,DefaultEdge](classOf[DefaultEdge])
    nodes.keys.foreach ( graph.addVertex(_) )
    edges.foreach { case ((from,to),any) => graph.addEdge(from,to) }
    println("Nodes: " + graph.vertexSet.size)
    println("Paths: " + graph.edgeSet.size)
    println(graph.toString)
    
    val path = new DijkstraShortestPath(graph, new TowerOfHanoiState(Array(1,1,1,1,1,1,1,1)), new TowerOfHanoiState(Array(3,3,3,3,3,3,3,3)))
    
    println(path.getPathLength())
    println(path.getPathEdgeList())
  }
  
  case class HanoiMoves( state:TowerOfHanoiState,nodes:ConcurrentMap[TowerOfHanoiState,Any],edges:ConcurrentMap[Tuple2[TowerOfHanoiState,TowerOfHanoiState],Any]) extends RecursiveAction {
    var tasks = List[HanoiMoves]()
    def move(from:Int,to:Int) = {
      if ( state.isValidMove(from,to) ) {
        val next = state.move(from,to)
        edges.putIfAbsent((state,next),0)
        if ( nodes.putIfAbsent(next,0) == None ) {
            val task = new HanoiMoves(next,nodes,edges)
            tasks ::= task
            task.fork()
        }
      }
    }
    override def compute = {
      nodes.putIfAbsent(state,0)
      for ( i <- 0.to(2) )
        for ( j <- 1.to(2) )
          move(i,(i+j)%3)
      tasks foreach ( _.join )
    }
  }
}