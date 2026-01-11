package com.aionemu.gameserver.geoEngine.scene;

import com.aionemu.gameserver.geoEngine.bounding.BoundingBox;
import com.aionemu.gameserver.geoEngine.bounding.BoundingVolume;
import com.aionemu.gameserver.geoEngine.collision.Collidable;
import com.aionemu.gameserver.geoEngine.collision.CollisionIntention;
import com.aionemu.gameserver.geoEngine.collision.CollisionResult;
import com.aionemu.gameserver.geoEngine.collision.CollisionResults;
import com.aionemu.gameserver.geoEngine.collision.UnsupportedCollisionException;
import com.aionemu.gameserver.geoEngine.math.Matrix3f;
import com.aionemu.gameserver.geoEngine.math.Ray;
import com.aionemu.gameserver.geoEngine.math.Vector3f;

/**
 * A simple extension of {@link Spatial}. This class represents a single node
 * of a Nav Mesh. Only Triangular nodes are supported. It's assumed that none of
 * the nodes given to this class have a plane that is parallel to the (vertical) Z-axis.
 * <p>
 * A reference to neighboring nodes is maintained for use while pathfinding later,
 * along with an {@link #incenterX incenter} value to estimate how far away this node is from other points,
 * and an {@link #inRad} value to act as the path length off of this node.
 *
 * @author KAMIDAKI
 * @optimized Memory-optimized version with shared vertex buffer and primitive fields
 * Fix for Server: OLD CLASS from Aion Live Clássico
 * Target: Aion Classic 2.4
 */
public class NavGeometry extends Geometry {

    private NavGeometry edge1;
    private NavGeometry edge2;
    private NavGeometry edge3;
    private byte collisionIntentions;
    private byte materialId;

    // OTIMIZAÇÃO: Usar referência compartilhada ao invés de cópia
    private final float[] sharedVertexBuffer;
    private final int index0;
    private final int index1;
    private final int index2;

    // OTIMIZAÇÃO: Campos primitivos ao invés de array para incenter e inRad
    private final float incenterX;
    private final float incenterY;
    private final float incenterZ;
    private final float inRad;

    /**
     * Construtor otimizado que compartilha o buffer de vértices entre múltiplas geometrias.
     * Isso reduz drasticamente o uso de memória ao evitar duplicação de dados de vértices.
     *
     * @param sharedVertices Buffer compartilhado contendo todos os vértices
     * @param index0 Índice do primeiro vértice do triângulo
     * @param index1 Índice do segundo vértice do triângulo
     * @param index2 Índice do terceiro vértice do triângulo
     */
    public NavGeometry(float[] sharedVertices, int index0, int index1, int index2) {
        this.collisionIntentions = CollisionIntention.PHYSICAL.getId();

        // OTIMIZAÇÃO: Armazenar referência ao buffer compartilhado ao invés de copiar
        this.sharedVertexBuffer = sharedVertices;
        this.index0 = index0;
        this.index1 = index1;
        this.index2 = index2;

        // Calcular índices base
        int base0 = index0 * 3;
        int base1 = index1 * 3;
        int base2 = index2 * 3;

        // Ler vértices do buffer compartilhado
        float p1x = sharedVertices[base0];
        float p1y = sharedVertices[base0 + 1];
        float p1z = sharedVertices[base0 + 2];
        float p2x = sharedVertices[base1];
        float p2y = sharedVertices[base1 + 1];
        float p2z = sharedVertices[base1 + 2];
        float p3x = sharedVertices[base2];
        float p3y = sharedVertices[base2 + 1];
        float p3z = sharedVertices[base2 + 2];

        // Calcular comprimentos das arestas
        float edge1x = p2x - p1x;
        float edge1y = p2y - p1y;
        float edge1z = p2z - p1z;
        float edge2x = p3x - p2x;
        float edge2y = p3y - p2y;
        float edge2z = p3z - p2z;
        float edge3x = p1x - p3x;
        float edge3y = p1y - p3y;
        float edge3z = p1z - p3z;

        float edge1Len = (float) Math.sqrt(sumOfSquaredComps(edge1x, edge1y, edge1z));
        float edge2Len = (float) Math.sqrt(sumOfSquaredComps(edge2x, edge2y, edge2z));
        float edge3Len = (float) Math.sqrt(sumOfSquaredComps(edge3x, edge3y, edge3z));
        float lenSum = edge1Len + edge2Len + edge3Len;

        // OTIMIZAÇÃO: Armazenar incenter em campos primitivos ao invés de array
        this.incenterX = ((edge2Len * p1x) + (edge3Len * p2x) + (edge1Len * p3x)) / lenSum;
        this.incenterY = ((edge2Len * p1y) + (edge3Len * p2y) + (edge1Len * p3y)) / lenSum;
        this.incenterZ = ((edge2Len * p1z) + (edge3Len * p2z) + (edge1Len * p3z)) / lenSum;
        this.inRad = ((float) Math.sqrt(lenSum * (lenSum - edge1Len) * (lenSum - edge2Len) * (lenSum - edge3Len))) / lenSum;
    }

    private float sumOfSquaredComps(float x, float y, float z) {
        return (x * x) + (y * y) + (z * z);
    }

    public void setEdge1(NavGeometry connection) {
        edge1 = connection;
    }

    public void setEdge2(NavGeometry connection) {
        edge2 = connection;
    }

    public void setEdge3(NavGeometry connection) {
        edge3 = connection;
    }

    public NavGeometry getEdge1() {
        return edge1;
    }

    public NavGeometry getEdge2() {
        return edge2;
    }

    public NavGeometry getEdge3() {
        return edge3;
    }

    public byte getEdgeMatching(NavGeometry tri) {
        if (edge1 == tri) return 1;
        if (edge2 == tri) return 2;
        if (edge3 == tri) return 3;
        return 0;
    }

    /**
     * Finds the closest point on this {@link NavGeometry} to the given point.
     * <p>
     * Note: This is not currently implemented, and will instead return the closest
     * vertex to the given point, or the incenter if it's closer than any vertex.
     *
     * @param x -- the x-component of the given point.
     * @param y -- the y-component of the given point.
     * @param z -- the z-component of the given point.
     * @return A float[] containing the x, y, z components, in that order, of
     * the closest point on this {@link NavGeometry} to the given point.
     */
    public float[] getClosestPoint(float x, float y, float z) {
        //FIXME: Implement proper algorithm; consider com.aionemu.gameserver.geoEngine.math.Plane#getClosestPoint(Vector3f)
        float[] v0 = getVertex(0);
        float[] v1 = getVertex(1);
        float[] v2 = getVertex(2);

        float d0 = manhattanDistance(v0[0], v0[1], v0[2], x, y, z);
        float d1 = manhattanDistance(v1[0], v1[1], v1[2], x, y, z);
        float d2 = manhattanDistance(v2[0], v2[1], v2[2], x, y, z);
        float dIn = manhattanDistance(incenterX, incenterY, incenterZ, x, y, z);

        float min = Math.min(d0, Math.min(d1, Math.min(d2, dIn)));
        if (min == d0) return v0;
        if (min == d1) return v1;
        if (min == d2) return v2;
        return new float[] {incenterX, incenterY, incenterZ};
    }

    public float[] getVertex(int i) {
        int base;
        switch (i) {
            case 0:
                base = index0 * 3;
                break;
            case 1:
                base = index1 * 3;
                break;
            case 2:
                base = index2 * 3;
                break;
            default:
                throw new IllegalArgumentException("Vertex index must be 0, 1, or 2");
        }
        return new float[] {
                sharedVertexBuffer[base],
                sharedVertexBuffer[base + 1],
                sharedVertexBuffer[base + 2]
        };
    }

    public float[][] getEndpoints(byte edge) {
        int base0, base1;
        switch (edge) {
            case 1:
                base0 = index0 * 3;
                base1 = index1 * 3;
                break;
            case 2:
                base0 = index1 * 3;
                base1 = index2 * 3;
                break;
            case 3:
                base0 = index2 * 3;
                base1 = index0 * 3;
                break;
            default:
                assert false : "NavGeometry: Unknown edge: " + edge;
                return null;
        }

        return new float[][] {
                {sharedVertexBuffer[base0], sharedVertexBuffer[base0 + 1], sharedVertexBuffer[base0 + 2]},
                {sharedVertexBuffer[base1], sharedVertexBuffer[base1 + 1], sharedVertexBuffer[base1 + 2]}
        };
    }

    public float getInRad() {
        return inRad;
    }

    public float getPriority(float x, float y, float z) {
        return manhattanDistance(incenterX, incenterY, incenterZ, x, y, z);
    }

    private float manhattanDistance(float x1, float y1, float z1, float x2, float y2, float z2) {
        return Math.abs(x1 - x2) + Math.abs(y1 - y2) + Math.abs(z1 - z2);
    }

    public boolean isTowardsEdge(byte edge, float[] vec) {
        int base0 = index0 * 3;
        int base1 = index1 * 3;
        int base2 = index2 * 3;

        float p0x = sharedVertexBuffer[base0];
        float p0y = sharedVertexBuffer[base0 + 1];
        float p1x = sharedVertexBuffer[base1];
        float p1y = sharedVertexBuffer[base1 + 1];
        float p2x = sharedVertexBuffer[base2];
        float p2y = sharedVertexBuffer[base2 + 1];

        float vec1x, vec1y, vec2x, vec2y, vec3x, vec3y;
        switch (edge) {
            case 1:
                //Edge 1 is point 0 and 1
                vec1x = p0x - p2x;
                vec1y = p0y - p2y;
                vec2x = p1x - p2x;
                vec2y = p1y - p2y;
                vec3x = vec[0] - p2x;
                vec3y = vec[1] - p2y;
                break;
            case 2:
                //Edge 2 is point 1 and 2
                vec1x = p1x - p0x;
                vec1y = p1y - p0y;
                vec2x = p2x - p0x;
                vec2y = p2y - p0y;
                vec3x = vec[0] - p0x;
                vec3y = vec[1] - p0y;
                break;
            case 3:
                //Edge 3 is point 2 and 0
                vec1x = p2x - p1x;
                vec1y = p2y - p1y;
                vec2x = p0x - p1x;
                vec2y = p0y - p1y;
                vec3x = vec[0] - p1x;
                vec3y = vec[1] - p1y;
                break;
            default:
                return false;
        }

        boolean positive = crossZ(vec1x, vec1y, vec2x, vec2y) > 0;
        if (compareCross(crossZ(vec1x, vec1y, vec3x, vec3y), positive) &&
                compareCross(crossZ(vec3x, vec3y, vec2x, vec2y), positive)) {
            return true; //vec3 is between vec1 and vec2
        }
        return false;
    }

    /**
     * Creates a funnel to the specified edge's endpoints from the specified starting point and
     * compares the given direction vector to the walls of the funnel. If the direction vector
     * is between or on the walls of the funnel, this method will return true, false otherwise.
     * <p>
     * If an invalid edge number is passed in, edge 3 will be considered.
     *
     * @param edge - The number corresponding to the edge in question (valid range is 1 - 3)
     * @param dir - A vector from the starting point of the funnel to the destination
     * @param x - The starting point of the funnel's x component
     * @param y - The starting point of the funnel's y component
     * @return True if the direction vector passes through the specified edge, false otherwise.
     */
    public boolean isFunnelTowardsEdge(byte edge, float[] dir, float x, float y) {
        float[][] endpoints;
        if (edge == 1) {
            endpoints = getEndpoints((byte) 1);
        } else if (edge == 2) {
            endpoints = getEndpoints((byte) 2);
        } else {
            endpoints = getEndpoints((byte) 3);
        }

        float vec1x = endpoints[0][0] - x;
        float vec1y = endpoints[0][1] - y;
        float vec2x = endpoints[1][0] - x;
        float vec2y = endpoints[1][1] - y;

        boolean positive = crossZ(vec1x, vec1y, vec2x, vec2y) > 0;
        if (compareCross(crossZ(vec1x, vec1y, dir[0], dir[1]), positive) &&
                compareCross(crossZ(dir[0], dir[1], vec2x, vec2y), positive)) {
            return true; //dir is between vec1 and vec2
        }
        return false;
    }

    private static float crossZ(float x1, float y1, float x2, float y2) {
        return (x1 * y2) - (y1 * x2);
    }

    private static boolean compareCross(float crossZ, boolean positive) {
        return positive ? crossZ >= 0 : crossZ <= 0;
    }

    @Override
    public int collideWith(Collidable other, CollisionResults results) throws UnsupportedCollisionException {
        if (other instanceof Ray) {
            if (!worldBound.intersects(((Ray) other))) {
                return 0;
            }

            int base0 = index0 * 3;
            int base1 = index1 * 3;
            int base2 = index2 * 3;

            Vector3f intersection = new Vector3f();
            Vector3f p1 = new Vector3f(sharedVertexBuffer[base0], sharedVertexBuffer[base0 + 1], sharedVertexBuffer[base0 + 2]);
            Vector3f p2 = new Vector3f(sharedVertexBuffer[base1], sharedVertexBuffer[base1 + 1], sharedVertexBuffer[base1 + 2]);
            Vector3f p3 = new Vector3f(sharedVertexBuffer[base2], sharedVertexBuffer[base2 + 1], sharedVertexBuffer[base2 + 2]);

            if (((Ray) other).intersectWhere(p1, p2, p3, intersection)) {
                Vector3f displacement = intersection.subtract(((Ray) other).getOrigin());
                float distance = displacement.length();
                if (distance > ((Ray) other).limit) {
                    return 0;
                }

                CollisionResult res = new CollisionResult(intersection, distance);
                res.setGeometry(this);
                results.addCollision(res);

                return 1;
            }
            return 0;
        } else if (other instanceof BoundingBox) {
            if (worldBound.intersects((BoundingBox) other)) {
                int base0 = index0 * 3;
                int base1 = index1 * 3;
                int base2 = index2 * 3;

                Vector3f p1 = new Vector3f(sharedVertexBuffer[base0], sharedVertexBuffer[base0 + 1], sharedVertexBuffer[base0 + 2]);
                Vector3f p2 = new Vector3f(sharedVertexBuffer[base1], sharedVertexBuffer[base1 + 1], sharedVertexBuffer[base1 + 2]);
                Vector3f p3 = new Vector3f(sharedVertexBuffer[base2], sharedVertexBuffer[base2 + 1], sharedVertexBuffer[base2 + 2]);

                if (((BoundingBox) other).intersectsTriangle(p1, p2, p3)) {
                    Vector3f center = worldBound.getCenter();
                    CollisionResult res = new CollisionResult(center,
                            center.distance(((BoundingBox) other).getCenter()));
                    res.setGeometry(this);
                    results.addCollision(res);
                    return 1;
                }
            }
            return 0;
        } else {
            throw new UnsupportedCollisionException();
        }
    }

    @Override
    public void updateModelBound() {
        int base0 = index0 * 3;
        int base1 = index1 * 3;
        int base2 = index2 * 3;

        float v0x = sharedVertexBuffer[base0];
        float v0y = sharedVertexBuffer[base0 + 1];
        float v0z = sharedVertexBuffer[base0 + 2];
        float v1x = sharedVertexBuffer[base1];
        float v1y = sharedVertexBuffer[base1 + 1];
        float v1z = sharedVertexBuffer[base1 + 2];
        float v2x = sharedVertexBuffer[base2];
        float v2y = sharedVertexBuffer[base2 + 1];
        float v2z = sharedVertexBuffer[base2 + 2];

        Vector3f min = new Vector3f();
        Vector3f max = new Vector3f();

        min.setX(Math.min(v0x, Math.min(v1x, v2x)));
        min.setY(Math.min(v0y, Math.min(v1y, v2y)));
        min.setZ(Math.min(v0z, Math.min(v1z, v2z)));

        max.setX(Math.max(v0x, Math.max(v1x, v2x)));
        max.setY(Math.max(v0y, Math.max(v1y, v2y)));
        max.setZ(Math.max(v0z, Math.max(v1z, v2z)));

        if (worldBound instanceof BoundingBox) {
            ((BoundingBox) worldBound).setMinMax(min, max);
        } else {
            worldBound = new BoundingBox(min, max);
        }
    }

    public void setModelBound(BoundingVolume modelBound) {
        this.worldBound = modelBound;
    }

    public int getVertexCount() {
        return 3;
    }

    public int getTriangleCount() {
        return 1;
    }

    public byte getCollisionIntentions() {
        return collisionIntentions;
    }
    public void setCollisionIntentions(byte collisionIntentions) {
        this.collisionIntentions = collisionIntentions;
    }

    @Override
    public byte getMaterialId() {
        return materialId;
    }

    public void setMaterialId(byte materialId) {
        this.materialId = materialId;
    }

    @Override
    public short getCollisionFlags() {
        return (short) ((collisionIntentions << 8) | (materialId & 0xFF));
    }

    @Override
    public void setCollisionFlags(short flags) {
        this.materialId = (byte) (flags & 0xFF);
        this.collisionIntentions = (byte) (flags >> 8);
    }


    public void setTransform(Matrix3f rotation, Vector3f loc, Vector3f scale) {
        throw new UnsupportedOperationException();
    }

    public String toString() {
        return "(" + incenterX + ", " + incenterY + ", " + incenterZ + ") " + super.toString();
    }
}