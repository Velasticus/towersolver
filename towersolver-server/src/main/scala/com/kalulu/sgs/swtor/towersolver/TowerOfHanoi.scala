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


class PublicEdge[V] extends DefaultEdge {
  def from : V = super.getSource.asInstanceOf[V]
  def to : V = super.getTarget.asInstanceOf[V]
}


class TowerOfHanoiSolver(degree:Int) {
  val graph = {
    val state = TowerOfHanoiState(Array.fill(degree)(1))
    val nodes : ConcurrentMap[TowerOfHanoiState,Any] = new ConcurrentHashMap[TowerOfHanoiState,Any]()
    val edges : ConcurrentMap[Tuple2[TowerOfHanoiState,TowerOfHanoiState],Any] = new ConcurrentHashMap[Tuple2[TowerOfHanoiState,TowerOfHanoiState],Any]()
    val fj = new ForkJoinPool()

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
    
    val task = new HanoiMoves(state,nodes,edges)
    fj.invoke(task)
    task.join
    
    val graph = new SimpleGraph[TowerOfHanoiState,PublicEdge[TowerOfHanoiState]](classOf[PublicEdge[TowerOfHanoiState]])
    nodes.keys.foreach ( graph.addVertex(_) )
    edges.foreach { 
      case ((from,to),any) => 
        graph.addEdge(from,to)
    }
    
    graph
  }
  
  def findShortestPath(from:TowerOfHanoiState,to:TowerOfHanoiState) = {
    new DijkstraShortestPath(graph, from, to).getPathEdgeList()
  }
  def findShortestPathMoves(from:TowerOfHanoiState,to:TowerOfHanoiState) = {
    var currNode = from
    findShortestPath(from,to).map {
      case edge => if(currNode == edge.from) {
        currNode = edge.to
        edge.from.findMove(edge.to) 
      } else {
        currNode = edge.from
        edge.to.findMove(edge.from) 
      }
    }
  }
}

object TowerOfHanoiSolver {
  def apply(degree:Int) = {
    new TowerOfHanoiSolver(degree)
  }

  def main(args:Array[String]) : Unit = {
    
    val solver = TowerOfHanoiSolver(3)
    println("Nodes: " + solver.graph.vertexSet.size)
    println("Paths: " + solver.graph.edgeSet.size)
    println(solver.graph.toString)
    
    val start = TowerOfHanoiState(1,1,1)
    val end = TowerOfHanoiState(3,3,3)
    val path = solver.findShortestPath(start,end)
    val pathMoves = solver.findShortestPathMoves(start,end)
    
    println(path.length)
    println(path)
    
    pathMoves.foreach { case((from,to),idx) =>
      println("Move from: " + from + " to: " + to)
    }
  }
  
}