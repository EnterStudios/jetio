package org.jetio;

import java.io.IOException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jetio.lifecycle.Lifecycle;
import org.jetlang.channels.Channel;
import org.jetlang.core.Callback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** @author <a href="mailto:peter.royal@pobox.com">peter royal</a> */
abstract class AbstractSelector implements Callback<Event>, Runnable, Lifecycle {
    @SuppressWarnings( { "NonConstantLogger" } )
    protected final Logger logger = LoggerFactory.getLogger( getClass() );

    private final List<Session> toAdd = Collections.synchronizedList( new ArrayList<Session>() );

    private final Channel<DataEvent<IOException>> failed;
    protected final SelectionOp op;
    private final Configuration config;
    private final Thread thread;
    protected final Selector selector;
    private final AtomicBoolean waking = new AtomicBoolean( false );

    AbstractSelector( SelectionOp op, Configuration config, Channel<DataEvent<IOException>> failed )
        throws IOException
    {
        this.op = op;
        this.config = config;

        this.failed = failed;

        this.selector = Selector.open();
        this.thread = new Thread( this, op + " " + this.config.getName() + "-" + config.getCounter() );
    }

    /**
     * Handle a selected key
     *
     * @param key     Selected key
     * @param session Session for this key
     */
    protected abstract void selected( SelectionKey key, Session session ) throws IOException;

    @Override
    public void onMessage( Event event ) {
        Session session = event.session();

        logger.debug( "adding {} to {} selector queue", session, op );

        toAdd.add( session );

        if ( waking.compareAndSet( false, true ) ) {
            selector.wakeup();
        }
    }

    protected abstract void addToSelector( Session session ) throws IOException;

    @Override
    public void run() {
        while ( !Thread.interrupted() ) {
            try {
                selector.select();
            } catch( CancelledKeyException e ) {
                logger.debug( "Key was cancelled mid-select, ignoring", e );
            } catch( IOException e ) {
                logger.error( "Error while selecting for " + op + "s", e );
                break;
            }

            if ( Thread.interrupted() ) {
                logger.debug( "interrupted..." );
                break;
            }

            processAddQueue();

            for ( SelectionKey key : selector.selectedKeys() ) {
                Session session = (Session) key.attachment();

                try {
                    selected( key, session );
                } catch( IOException e ) {
                    failed.publish( new DataEvent<IOException>( session, e ) );
                }
            }
        }

        logger.debug( "{} selector thread exiting...", op );
    }

    private void processAddQueue() {
        Session[] sessions;

        synchronized( toAdd ) {
            sessions = toAdd.toArray( new Session[toAdd.size()] );
            toAdd.clear();

            waking.set( false );
        }

        for ( Session session : sessions ) {
            try {
                addToSelector( session );
            } catch( IOException e ) {
                failed.publish( new DataEvent<IOException>( session, e ) );
            }
        }
    }

    @Override
    public void start() throws IOException {
        thread.start();
    }

    @Override
    public void dispose() {
        thread.interrupt();

        try {
            thread.join( config.getDisposalWaitTime() );
        } catch( InterruptedException e ) {
            logger.error( "Interrupted while waiting for selector thread to complete", e );

            Thread.currentThread().interrupt();
        }
    }
}
