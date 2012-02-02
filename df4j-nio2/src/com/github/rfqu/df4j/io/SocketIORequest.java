package com.github.rfqu.df4j.io;

import java.nio.ByteBuffer;

import com.github.rfqu.df4j.core.Link;

public abstract class SocketIORequest extends Link {
    protected AsyncSocketChannel channel;
    protected ByteBuffer buffer;
    protected boolean readOp;
    protected boolean inTrans=false;
    
    public SocketIORequest(int capacity, boolean direct) {
        if (direct) {
            buffer=ByteBuffer.allocateDirect(capacity);
        } else {
            buffer=ByteBuffer.allocate(capacity);
        }
    }
    
    public SocketIORequest(ByteBuffer buf) {
        this.buffer = buf;
    }
    
    public void clear() {
        buffer.clear();
    }

    public void read(AsyncSocketChannel channel) {
        if (inTrans) {
            throw new IllegalStateException("SocketIORequest.read: in "+(readOp?"read":"write")+" already");
        }
        inTrans=true;
        readOp=true;
        this.channel=channel;
        buffer.clear();
        channel.read(this);
    }


    public void write(AsyncSocketChannel channel) {
        if (inTrans) {
            throw new IllegalStateException("SocketIORequest.write: in "+(readOp?"read":"write")+" already");
        }
        inTrans=true;
        readOp=false;
        this.channel=channel;
        buffer.flip();
        channel.write(this);
    }

    protected void requestCompleted(Integer result) {
        if (!inTrans) {
            throw new IllegalStateException("SocketIORequest "+(readOp?"read":"write")+" completed but not in trans");
        }
        inTrans=false;
        buffer.flip();
        if (readOp) {
            readCompleted(result);
        } else {
            writeCompleted(result);
        }
    }

    protected void writeCompleted(Integer result) {
        // TODO Auto-generated method stub
        
    }

    protected void readCompleted(Integer result) {
        // TODO Auto-generated method stub
        
    }

    protected void requestFailed(Throwable exc) {
        if (!inTrans) {
            throw new IllegalStateException("SocketIORequest "+(readOp?"read":"write")+" failed but not in trans");
        }
        inTrans=false;
        if (readOp) {
            readFailed(exc);
        } else {
            writeFailed(exc);
        }
    }

    protected void writeFailed(Throwable exc) {
        // TODO Auto-generated method stub
        
    }

    protected void readFailed(Throwable exc) {
        // TODO Auto-generated method stub
        
    }

}
