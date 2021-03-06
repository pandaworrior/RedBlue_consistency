package edu.rice.rubis.servlets;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.EmptyStackException;
import java.util.Properties;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicInteger;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.UnavailableException;

import replicationlayer.core.txstore.scratchpad.rdbms.jdbc.TxMudConnection;
import replicationlayer.core.txstore.scratchpad.rdbms.jdbc.TxMudDriver;

import runtimelogic.shadowoperationcreator.shadowoperation.ShadowOperation;
import runtimelogic.shadowoperationcreator.ShadowOperationCreator;
import runtimelogic.staticinformation.StaticFPtoWPsStore;
import runtimelogic.weakestpreconditionchecker.WeakestPreconditionChecker;

import edu.rice.rubis.servlets.AuctionManager;
import edu.rice.rubis.servlets.Config;

/** This class contains the configuration for the servlets
 * like the path of HTML files, etc ...
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.0
 */

public class Database
{

  /** Controls connection pooling */
	private static final boolean enablePooling = true;
	/** Stack of available connections (pool) */
	private static Stack<TxMudConnection> freeConnections = null;
	private static Properties dbProperties = null;
	
	static AtomicInteger aborts =new AtomicInteger(0);
    static AtomicInteger commitedtxn =new AtomicInteger(0);
    static int transactions = 0;
    static long startmi=0;
    static long endmi=0;
	public static RUBIS_TxMud_Proxy proxy;
	
	static String annotatedSchemaFilePath = "/home/chengli/newJava/src/applications/RUBiStxmud/database/rubis_sieve.sql";
    static String weakestpreconditionFilePath = "/home/chengli/newJava/src/applications/RUBiStxmud/rubis_sieve_input.txt";
	
	
	public static void init() throws ServletException {
		System.out.println("======HTML FILES:" + Config.HTMLFilesPath);
		System.out.println("======Database FILES:" + Config.DatabaseProperties);
		InputStream in = null;
		
		try {
			// Get the properties for the database connection
			dbProperties = new Properties();
			in = new FileInputStream(Config.DatabaseProperties);
			dbProperties.load(in);
			// load the driver
			Class.forName("replicationlayer.core.txstore.scratchpad.rdbms.jdbc.TxMudDriver");

			freeConnections = new Stack<TxMudConnection>();
			initializeConnections();
		} catch (FileNotFoundException f) {
			throw new UnavailableException(
					"Couldn't find file mysql.properties: " + f + "<br>");
		} catch (IOException io) {
			throw new UnavailableException(
					"Cannot open read mysql.properties: " + io + "<br>");
		} catch (ClassNotFoundException c) {
			throw new UnavailableException("Couldn't load database driver: "
					+ c + "<br>");
		} catch (SQLException s) {
			throw new UnavailableException("Couldn't get database connection: "
					+ s + "<br>");
		} finally {
			try {
				if (in != null)
					in.close();
			} catch (Exception e) {
			}
		}
	}//method
	/**
	 * Initialize the pool of connections to the database. The caller must
	 * ensure that the driver has already been loaded else an exception will be
	 * thrown.
	 * 
	 * @exception SQLException
	 *                if an error occurs
	 */
	public static synchronized void initializeConnections() throws SQLException {
		
    	//load the weakest preconditions here
    	StaticFPtoWPsStore sFpStore = new StaticFPtoWPsStore(weakestpreconditionFilePath);
		sFpStore.loadAllStaticOutput();
		WeakestPreconditionChecker.setStaticFPtoWPsStore(sFpStore);
		System.out.println("Initializing database pool for Sifter");
		if (enablePooling){
			for (int i = 0; i < Config.DatabasePool; i++) {
				// Get connections to the database
				TxMudDriver.proxy = proxy.imp;
				TxMudConnection c = (TxMudConnection) DriverManager.getConnection("jdbc:txmud:test");
				c.setAutoCommit(false);
				c.shdOpCreator = new ShadowOperationCreator(annotatedSchemaFilePath, 
						dbProperties.getProperty("datasource.url"),
						dbProperties.getProperty("datasource.username"),
						dbProperties.getProperty("datasource.password") , proxy.getMyGlobalProxyId(), proxy.getTotalProxyNum(),
						c);
                System.out.println("Initializing database pool " + i);
				freeConnections.push(c);
			}
		System.out.println("Database pool was initialized. #connections:"+Config.DatabasePool);	
		}
	}
	/**
	 * Closes a <code>Connection</code>.
	 * 
	 * @param connection
	 *            to close
	 */
	public static void closeConnection(Connection connection) {
		try {
			connection.close();
		} catch (Exception e) {

		}
	}

	/**
	 * Gets a connection from the pool (round-robin)
	 * 
	 * @return a <code>Connection</code> or null if no connection is available
	 */
	public static synchronized TxMudConnection getConnection() {
		long time = System.currentTimeMillis();
		boolean isMeasurementInterval=(time > startmi && time < endmi);
		
		if (enablePooling) {
			try {
				// Wait for a connection to be available
				TxMudConnection c = (TxMudConnection) freeConnections.pop();
				if(isMeasurementInterval) transactions++;
				return c;
			} catch (EmptyStackException e) {
				System.out.println("Connection pool Out of connections.");
				return null;
			}
		} else {
			try {
				TxMudDriver.proxy = proxy.imp;
				TxMudConnection c = (TxMudConnection) DriverManager.getConnection("jdbc:txmud:test");
				c.setAutoCommit(false);
				c.shdOpCreator = new ShadowOperationCreator(annotatedSchemaFilePath, 
						dbProperties.getProperty("datasource.url"),
						dbProperties.getProperty("datasource.username"),
						dbProperties.getProperty("datasource.password") , proxy.getMyGlobalProxyId(), proxy.getTotalProxyNum(),
						c);
				if(isMeasurementInterval) transactions++;
				return c;
			} catch (SQLException ex) {
				System.out.println("Error when connecting to the database. pw:"
						+ dbProperties.getProperty("datasource.password")
						+ " url:" + dbProperties.getProperty("datasource.url")
						+ " username:"
						+ dbProperties.getProperty("datasource.username"));
				ex.printStackTrace();
				return null;
			}
		}
	}
	
	
	/**
	 * Release a connection to the pool. Changing individual method in each
	 * servelet to one
	 */
	
	public static void closeConnection(PreparedStatement stmt, Connection conn){
		try
	    {
	      if (stmt != null)
	        stmt.close(); // close statement
	      if (conn != null)
	        releaseConnection(conn);
	    }
	    catch (Exception ignore)
	    {
	    }
	}
	
	/**
	 * commit a transaction
	 */
	
	public static void commit(TxMudConnection conn){
		long time = System.currentTimeMillis();
		boolean isMeasurementInterval=(time > startmi && time < endmi);
		try{
			conn.commit();
			if(isMeasurementInterval)
				commitedtxn.incrementAndGet();
		}catch (SQLException e){
			if(isMeasurementInterval)
				aborts.incrementAndGet();
			System.err.println("Restarting TX because of a database problem (hopefully just a conflict) total aborts within the MI so far:"+aborts);
			//e.printStackTrace();
		}
	}
	
	/**
	 * rollback a transaction
	 */
	
	public static void rollback(TxMudConnection conn){
		try{
			conn.rollback();
		}catch (SQLException e){
			e.printStackTrace();
		}
	}

	/**
	 * Releases a connection to the pool.
	 * 
	 * @param c
	 *            the connection to release
	 */
	public static synchronized void releaseConnection(Connection c) {
		if (enablePooling) {
			boolean mustNotify = freeConnections.isEmpty();
			freeConnections.push((TxMudConnection)c);
			// Wake up one servlet waiting for a connection (if any)
//			if (mustNotify)
//				notifyAll();
		} else {
			closeConnection(c);
		}

	}

	/**
	 * Release all the connections to the database.
	 * 
	 * @exception SQLException
	 *                if an error occurs
	 */
	public synchronized void finalizeConnections() throws SQLException {
		if (enablePooling) {
			TxMudConnection c = null;
			while (!freeConnections.isEmpty()) {
				c = (TxMudConnection) freeConnections.pop();
				c.close();
			}
		}
	}
}

