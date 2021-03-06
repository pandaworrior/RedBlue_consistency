package edu.rice.rubis.servlets;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import txstore.scratchpad.rdbms.jdbc.TxMudConnection;
import txstore.scratchpad.rdbms.util.rubis.DBRUBISShdEmpty;

/** This servlets displays the list of bids regarding an item.
 * It must be called this way :
 * <pre>
 * http://..../ViewUserInfo?itemId=xx where xx is the id of the item
 * /<pre>
 */

public class ViewBidHistory extends HttpServlet
{


//  public int getPoolSize()
//  {
//    return Config.ViewBidHistoryPoolSize;
//  }

  /** List the bids corresponding to an item */
  private boolean listBids(Integer itemId, PreparedStatement stmt, TxMudConnection conn, ServletPrinter sp)
  {
    float bid;
    int userId;
    String bidderName, date;
    ResultSet rs = null;

    // Get the list of the user's last bids
    try
    {
      stmt =
        conn.prepareStatement(
          "SELECT * FROM bids WHERE item_id=? ORDER BY date DESC");
      stmt.setInt(1, itemId.intValue());
      rs = stmt.executeQuery();
      if (!rs.first())
      {
        sp.printHTML(
          "<h3>There is no bid corresponding to this item.</h3><br>");
        //set shadow operation
        try{
    	    DBRUBISShdEmpty dEm = DBRUBISShdEmpty.createOperation();
    	    conn.setShadowOperation(dEm, 0);
        }catch (IOException e){
    		// TODO Auto-generated catch block
    		e.printStackTrace();
        }
        try{
      	    DBRUBISShdEmpty dEm = DBRUBISShdEmpty.createOperation();
      	    conn.setShadowOperation(dEm, 0);
          }catch (IOException e){
      		// TODO Auto-generated catch block
      		e.printStackTrace();
          }
        Database.commit(conn);
        Database.closeConnection(stmt, conn);
        return false;
      }
    }
    catch (SQLException e)
    {
      sp.printHTML("Exception getting bids list: " + e + "<br>");
      Database.rollback(conn);
      Database.closeConnection(stmt, conn);
      return false;
    }

    sp.printBidHistoryHeader();
    try
    {
      do
      {
        // Get the bids
        date = rs.getString("date");
        bid = rs.getFloat("bid");
        userId = rs.getInt("user_id");

        ResultSet urs = null;
        try
        {
          stmt = conn.prepareStatement("SELECT nickname FROM users WHERE id=?");
          stmt.setInt(1, userId);
          urs = stmt.executeQuery();
          if (!urs.first())
          {
            sp.printHTML("This user does not exist in the database.<br>");
            //set shadow operation
            try{
        	    DBRUBISShdEmpty dEm = DBRUBISShdEmpty.createOperation();
        	    conn.setShadowOperation(dEm, 0);
            }catch (IOException e){
        		// TODO Auto-generated catch block
        		e.printStackTrace();
            }
            Database.commit(conn);
            Database.closeConnection(stmt, conn);
            return false;
          }
          bidderName = urs.getString("nickname");
        }
        catch (SQLException e)
        {
          sp.printHTML("Couldn't get bidder name: " + e + "<br>");
          Database.rollback(conn);
          Database.closeConnection(stmt, conn);
          return false;
        }
        sp.printBidHistory(userId, bidderName, bid, date);
      }
      while (rs.next());
    }
    catch (SQLException e)
    {
      sp.printHTML("Exception getting bid: " + e + "<br>");
      Database.rollback(conn);
      Database.closeConnection(stmt, conn);
      return false;
    }
    sp.printBidHistoryFooter();
    return true;
  }

  public void doGet(HttpServletRequest request, HttpServletResponse response)
    throws IOException, ServletException
  {
    doPost(request, response);
  }

  public void doPost(HttpServletRequest request, HttpServletResponse response)
    throws IOException, ServletException
  {
    String value = request.getParameter("itemId");
    Integer itemId;
    String itemName;
    ResultSet rs = null;
    ServletPrinter sp = null;
    PreparedStatement stmt = null;
    TxMudConnection conn = null;

    sp = new ServletPrinter(response, "ViewBidHistory");

    if ((value == null) || (value.equals("")))
    {
      sp.printHTMLheader("RUBiS ERROR: View bids history");
      sp.printHTML("<h3>You must provide an item identifier !<br></h3>");
      sp.printHTMLfooter();
      return;
    }
    else
      itemId = new Integer(value);
    if (itemId.intValue() == -1)
      sp.printHTML("ItemId is -1: this item does not exist.<br>");

    sp.printHTMLheader("RUBiS: Bid history");

    // get the item
    try
    {
      conn = Database.getConnection();
      stmt = conn.prepareStatement("SELECT name FROM items WHERE id=?");
      stmt.setInt(1, itemId.intValue());
      rs = stmt.executeQuery();
    }
    catch (Exception e)
    {
      sp.printHTML("Failed to execute Query for item in table items: " + e);
      Database.rollback(conn);
      Database.closeConnection(stmt, conn);
      return;
    }
    /**
    try
    {
      if (!rs.first())
      {
        stmt.close();
        stmt = conn.prepareStatement("SELECT name FROM old_items WHERE id=?");
        stmt.setInt(1, itemId.intValue());
        rs = stmt.executeQuery();
      }
    }
    catch (Exception e)
    {
      sp.printHTML("Failed to execute Query for item in table old_items: " + e);
      Database.closeConnection(stmt, conn);
      return;
    }
    */
    try
    {
      if (!rs.first())
      {
        sp.printHTML("<h2>This item does not exist!</h2>");
        try{
      	    DBRUBISShdEmpty dEm = DBRUBISShdEmpty.createOperation();
      	    conn.setShadowOperation(dEm, 0);
          }catch (IOException e){
      		// TODO Auto-generated catch block
      		e.printStackTrace();
          }
        Database.commit(conn);
        Database.closeConnection(stmt, conn);
        return;
      }
      itemName = rs.getString("name");
      sp.printHTML(
        "<center><h3>Bid History for " + itemName + "<br></h3></center>");
    }
    catch (Exception e)
    {
      sp.printHTML("This item does not exist (got exception: " + e + ")<br>");
      sp.printHTMLfooter();
      Database.rollback(conn);
      Database.closeConnection(stmt, conn);
      return;
    }

    boolean connAlive = listBids(itemId, stmt, conn, sp);
    // connAlive means we must close it. Otherwise we must NOT do a
    // double free
    if (connAlive) {
    	try{
      	    DBRUBISShdEmpty dEm = DBRUBISShdEmpty.createOperation();
      	    conn.setShadowOperation(dEm, 0);
          }catch (IOException e){
      		// TODO Auto-generated catch block
      		e.printStackTrace();
          }
    	Database.commit(conn);
        Database.closeConnection(stmt, conn);
    }
    sp.printHTMLfooter();
  }

  /**
  * Clean up the connection pool.
  */
  public void destroy()
  {
    super.destroy();
  }

}
