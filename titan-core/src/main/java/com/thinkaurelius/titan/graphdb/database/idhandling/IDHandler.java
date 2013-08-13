package com.thinkaurelius.titan.graphdb.database.idhandling;

import com.google.common.base.Preconditions;
import com.thinkaurelius.titan.diskstorage.ReadBuffer;
import com.thinkaurelius.titan.diskstorage.StaticBuffer;
import com.thinkaurelius.titan.diskstorage.WriteBuffer;
import com.thinkaurelius.titan.diskstorage.util.ByteBufferUtil;
import com.thinkaurelius.titan.diskstorage.util.StaticArrayBuffer;
import com.thinkaurelius.titan.diskstorage.util.WriteByteBuffer;
import com.thinkaurelius.titan.graphdb.idmanagement.IDManager;
import com.thinkaurelius.titan.graphdb.internal.RelationType;

/**
 * @author Matthias Broecheler (me@matthiasb.com)
 */

public class IDHandler {

    public final static StaticBuffer getKey(long id) {
        Preconditions.checkArgument(id >= 0);
        return ByteBufferUtil.getLongBuffer(id << 1);
    }

    public final static long getKeyID(StaticBuffer b) {
        long value = b.getLong(0);
        return value >>> 1;
    }

    private static final int PREFIX_BIT_LEN = 2;

    public static final int PROPERTY_DIR = 0;
    public static final int EDGE_OUT_DIR = 2;
    public static final int EDGE_IN_DIR = 3;

    private static final int getDirection(int dirID) {
        //0=out, 1=in
        return dirID & 1;
    }

    private static final int getRelationType(int dirID) {
        //0=property, 1=edge
        return dirID>>>1;
    }

    private static final int getDirectionID(int relationType, int direction) {
        Preconditions.checkArgument(relationType>=0 && relationType<=1 && direction>=0 && direction<=1);
        return (relationType<<1) + direction;
    }

    public static final boolean isValidDirection(final int dirId) {
        return dirId==PROPERTY_DIR || dirId==EDGE_IN_DIR || dirId==EDGE_OUT_DIR;
    }

    public final static int edgeTypeLength(long etid) {
        Preconditions.checkArgument(etid>0 && (etid<<1)>0);
        return VariableLong.positiveWithPrefixLength(IDManager.getTypeCount(etid<<1), PREFIX_BIT_LEN);
    }

    public final static void writeEdgeType(WriteBuffer out, long etid, int dirID) {
        Preconditions.checkArgument(etid>0 && (etid<<1)>0);
        Preconditions.checkArgument(isValidDirection(dirID));
        etid = (IDManager.getTypeCount(etid)<<1) + getDirection(dirID);
        VariableLong.writePositiveWithPrefix(out,etid,getRelationType(dirID), PREFIX_BIT_LEN);
    }

    public final static StaticBuffer getEdgeType(long etid, int dirID) {
        WriteBuffer b = new WriteByteBuffer(edgeTypeLength(etid));
        IDHandler.writeEdgeType(b, etid, dirID);
        return b.getStaticBuffer();
    }

    public final static long[] readEdgeType(ReadBuffer in) {
        long[] countPrefix = VariableLong.readPositiveWithPrefix(in, PREFIX_BIT_LEN);
        int dirID=getDirectionID((int)countPrefix[1],(int)(countPrefix[0]&1));
        countPrefix[1]=dirID;
        countPrefix[0]=countPrefix[0]>>>1;
        if (countPrefix[1]==PROPERTY_DIR)
            countPrefix[0] = IDManager.getPropertyKeyID(countPrefix[0]);
        else if (countPrefix[1]==EDGE_IN_DIR || countPrefix[1]==EDGE_OUT_DIR)
            countPrefix[0] = IDManager.getEdgeLabelID(countPrefix[0]);
        else throw new AssertionError("Invalid direction ID: " + countPrefix[1]);
        return countPrefix;
    }


    public final static void writeInlineEdgeType(WriteBuffer out, long etid) {
        long compressId = IDManager.getTypeCount(etid)<<1;
        if (IDManager.isPropertyKeyID(etid))
            compressId += 0;
        else if (IDManager.isEdgeLabelID(etid))
            compressId += 1;
        else throw new AssertionError("Invalid type id: " + etid);
        VariableLong.writePositive(out,compressId);
    }

    public final static long readInlineEdgeType(ReadBuffer in) {
        long compressId = VariableLong.readPositive(in);
        long id = compressId>>>1;
        switch((int)(compressId & 1)) {
            case 0:
                id = IDManager.getPropertyKeyID(id);
                break;
            case 1:
                id = IDManager.getEdgeLabelID(id);
                break;
            default: throw new AssertionError("Invalid type: " + compressId);
        }
        return id;
    }

    private static final StaticBuffer getPrefixed(int prefix) {
        Preconditions.checkArgument(prefix<(1<<PREFIX_BIT_LEN) && prefix>=0);
        byte[] arr = new byte[1];
        arr[0] = (byte)(prefix<<(Byte.SIZE-PREFIX_BIT_LEN));
        return new StaticArrayBuffer(arr);
    }

    public static final StaticBuffer[] getBounds(RelationType type) {
        int start, end;
        switch(type) {
            case PROPERTY:
                start = getRelationType(PROPERTY_DIR); end=start+1;
                break;
            case EDGE:
                start = getRelationType(EDGE_OUT_DIR); end=start+1;
                break;
            case RELATION:
                start = getRelationType(PROPERTY_DIR); end=getRelationType(EDGE_OUT_DIR)+1;
                break;
            default:
                throw new AssertionError("Unrecognized type:" + type);
        }
        Preconditions.checkArgument(end>start);
        return new StaticBuffer[]{getPrefixed(start),getPrefixed(end)};
    }

}
