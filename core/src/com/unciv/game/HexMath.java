package com.unciv.game;

import com.badlogic.gdx.math.Vector2;
import com.unciv.models.LinqCollection;

import java.util.ArrayList;
import java.util.HashSet;

public class HexMath
{

    public static Vector2 GetVectorForAngle(float angle)
    {
        return new Vector2((float)Math.sin(angle), (float) Math.cos(angle));
    }

    public static Vector2 GetVectorByClockHour(int hour)
    {
        return GetVectorForAngle((float) ((2 * Math.PI) * (hour / 12f)));
    }

    // HexCoordinates are a (x,y) vector, where x is the vector getting us to the top-left hex (e.g. 10 o'clock)
    // and y is the vector getting us to the top-right hex (e.g. 2 o'clock)

    // Each (1,1) vector effectively brings us up a layer.
    // For example, to get to the cell above me, I'll use a (1,1) vector.
    // To get to the cell below the cell to my bottom-right, I'll use a (-1,-2) vector.

    public static Vector2 Hex2WorldCoords(Vector2 hexCoord)
    {
        // Distance between cells = 2* normal of triangle = 2* (sqrt(3)/2) = sqrt(3)
        Vector2 xVector = GetVectorByClockHour(10).scl((float) Math.sqrt(3));
        Vector2 yVector = GetVectorByClockHour(2).scl((float) Math.sqrt(3));
        return xVector.scl(hexCoord.x).add(yVector.scl(hexCoord.y));
    }

    public static ArrayList<Vector2> GetAdjacentVectors(Vector2 origin){
        ArrayList<Vector2> vectors = new ArrayList<Vector2>();
        vectors.add(new Vector2(1, 0));
        vectors.add(new Vector2(1, 1));
        vectors.add(new Vector2(0, 1));
        vectors.add(new Vector2(-1, 0));
        vectors.add(new Vector2(-1, -1));
        vectors.add(new Vector2(0, -1));
        for(Vector2 vector : vectors) vector.add(origin);
        return vectors;
    }

    public static LinqCollection<Vector2> GetVectorsInDistance(Vector2 origin, int distance){
        HashSet<Vector2> hexesToReturn = new HashSet<Vector2>();
        HashSet<Vector2> oldHexes;
        HashSet<Vector2> newHexes = new HashSet<Vector2>();
        hexesToReturn.add(origin);
        newHexes.add(origin);
        for (int i = 0; i < distance; i++) {
            oldHexes = newHexes;
            newHexes = new HashSet<Vector2>();
            for (Vector2 vector : oldHexes) {
                for (Vector2 adjacentVector : GetAdjacentVectors(vector)){
                    if(hexesToReturn.contains(adjacentVector)) continue;
                    hexesToReturn.add(adjacentVector);
                    newHexes.add(adjacentVector);
                }
            }
        }
        return new LinqCollection<Vector2>(hexesToReturn);
    }

    public static int GetDistance(Vector2 origin, Vector2 destination){ // Yes, this is a dumb implementation. But I can't be arsed to think of a better one right now, other stuff to do.
        int distance = 0;
        while(true){
            if(GetVectorsInDistance(origin,distance).contains(destination)) return distance;
            distance++;
        }
    }

//    public static boolean IsWithinDistance(Vector2 a, Vector2 b, int distance){
//        return GetVectorsInDistance(a,distance).contains(b);
//        Vector2 distanceVector = a.sub(b);
//        if(distanceVector.x<0) distanceVector = new Vector2(-distanceVector.x,-distanceVector.y);
//
//        int distance = (int) Math.abs(distanceVector.x);
//        distanceVector = distanceVector.sub(distanceVector.x,distanceVector.x); // Zero X f distance, then we'll calculate Y
//        distance += Math.abs(distanceVector.y);
//        return distance;
//    }
}
