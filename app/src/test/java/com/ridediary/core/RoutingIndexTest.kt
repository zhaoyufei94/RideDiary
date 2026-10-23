package com.ridediary.core

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*
import kotlin.random.Random

class RoutingIndexTest {
    // Original exhaustive algorithm is an independent oracle for geometry and tie ordering.
    private fun exhaustive(f: Fix,route: RouteShape,previous: Double,forward: Double): Routing.Projection? {
        var accumulated=0.0; var best: Routing.Projection?=null; var score=Double.POSITIVE_INFINITY
        for((a,b) in route.points.zipWithNext()) {
            val len=meters(a,b)
            if(len<0.01) continue
            val cosLat=cos(Math.toRadians(a.lat))
            val bx=(b.lon-a.lon)*111_195*cosLat; val by=(b.lat-a.lat)*111_195
            val fx=(f.lon-a.lon)*111_195*cosLat; val fy=(f.lat-a.lat)*111_195
            val u=((fx*bx+fy*by)/(len*len)).coerceIn(0.0,1.0)
            val along=accumulated+u*len; val error=hypot(fx-u*bx,fy-u*by)
            if(along>=previous-60 && along<=previous+forward) {
                val candidate=error+abs(along-previous)*0.015
                if(candidate<score) { best=Routing.Projection(along,error); score=candidate }
            }
            accumulated+=len
        }
        return best
    }
    @Test fun indexedProjectionMatchesExhaustiveOnLoopsCrossingsAndDuplicatePoints() {
        val points=(0..1800).map { i ->
            val x=i/90.0
            Fix(i*2000L,30+sin(x)*.003,120+sin(2*x)*.003)
        }.flatMapIndexed { i,p -> if(i%7==0) listOf(p,p) else listOf(p) }
        val route=RouteShape("r","r","WORK",points)
        val random=Random(32)
        repeat(1200) {
            val f=points[random.nextInt(points.size)].let { it.copy(lat=it.lat+random.nextDouble(-.0003,.0003)) }
            val previous=random.nextDouble(0.0,route.length+100)
            val forward=random.nextDouble(60.0,250.0)
            val expected=exhaustive(f,route,previous,forward)
            val actual=Routing.project(f,route,previous,forward)
            assertEquals(expected,actual)
        }
        assertEquals(points.zipWithNext().sumOf { (a,b)->meters(a,b) },route.length,0.0)
    }
}
