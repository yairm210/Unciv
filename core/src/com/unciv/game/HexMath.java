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

    public static ArrayList<Vector2> GetVectorsAtDistance(Vector2 origin, int distance){
        ArrayList<Vector2> vectors = new ArrayList<Vector2>();
        Vector2 Current = origin.cpy().sub(distance,distance); // start at 6 o clock
        for (int i = 0; i < distance; i++) { // From 6 to 8
            vectors.add(Current.cpy());
            vectors.add(origin.cpy().scl(2).sub(Current)); // Get vector on other side of cloick
            Current.add(1,0);
        }
        for (int i = 0; i < distance; i++) { // 8 to 10
            vectors.add(Current.cpy());
            vectors.add(origin.cpy().scl(2).sub(Current)); // Get vector on other side of cloick
            Current.add(1,1);
        }
        for (int i = 0; i < distance; i++) { // 10 to 12
            vectors.add(Current.cpy());
            vectors.add(origin.cpy().scl(2).sub(Current)); // Get vector on other side of cloick
            Current.add(1,1);
        };
        return vectors;
    }

    public static LinqCollection<Vector2> GetVectorsInDistance(Vector2 origin, int distance) {
        HashSet<Vector2> hexesToReturn = new HashSet<Vector2>();
        for (int i = 0; i < distance + 1; i++) {
            hexesToReturn.addAll(GetVectorsAtDistance(origin, i));
        }
        return new LinqCollection<Vector2>(hexesToReturn);
    }

    public static int GetDistance(Vector2 origin, Vector2 destination){ // Yes, this is a dumb implementation. But I can't be arsed to think of a better one right now, other stuff to do.
        int distance = 0;
        while(true){
            if(GetVectorsAtDistance(origin,distance).contains(destination)) return distance;
            distance++;
        }
    }

}
