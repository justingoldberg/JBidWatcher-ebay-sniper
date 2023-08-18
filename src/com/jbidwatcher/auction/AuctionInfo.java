package com.jbidwatcher.auction;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

/*
 * @brief Generic auction information, generally information that
 * needed to be retrieved.
 *
 * Program-specific information (next update time, what auction
 * server, etc.) is stored in AuctionEntry
 */

import com.jbidwatcher.util.Currency;
import com.jbidwatcher.util.config.*;
import com.jbidwatcher.util.*;
import com.jbidwatcher.util.db.*;
import com.jbidwatcher.util.db.ActiveRecord;
import com.jbidwatcher.util.xml.XMLElement;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class AuctionInfo extends AuctionCore {
  private String potentialThumbnail = null;
  private Object mServer = null; //  TODO --  This is a hack!
  //  It's so that the AuctionServer that creates this can record
  // 'who it is', so the AuctionEntry will pick it up.

  protected Seller mSeller;
  protected GZip mLoadedPage = null;

  /**
   * @brief Empty constructor, for XML parsing.
   *
   */
  public AuctionInfo() { }

  protected String[] infoTags = { "title", "seller", "highbidder", "bidcount", "start", "end",
                                "currently", "dutch", "reserve", "private", "content",
                                "shipping", "insurance", "buynow", "usprice", "fixed", "minimum",
                                "paypal", "location", "feedback", "percentage", "sellerinfo", "buy_now_us"};
  protected String[] getTags() { return infoTags; }

  protected void handleTag(int i, XMLElement curElement) {
    switch(i) {
      case 0:  //  Title
        setString(infoTags[i], XMLElement.decodeString(curElement.getContents()));
        break;
      case 1:  //  Seller name
        if(curElement.getChild("name") == null) {
          setSellerName(curElement.getContents());
        } else {
          mSeller = Seller.newFromXML(curElement);
        }
        break;
      case 2:  //  High bidder name
      case 18: //  Location of item
        setString(infoTags[i], curElement.getContents());
        break;
      case 3:  //  Bid count
        setInteger(infoTags[i], Integer.parseInt(curElement.getContents()));
        break;
      case 4:  //  Start date
      case 5:  //  End date
        setDate(infoTags[i], new Date(Long.parseLong(curElement.getContents())));
        break;
      case 6:  //  Current price
      case 11: //  Shipping cost
      case 12: //  Insurance cost
      case 13: //  Buy Now price
      case 22: //  Buy Now US price
      case 14: //  Current US price
      case 16: //  Minimum price/bid
        Currency amount = Currency.getCurrency(curElement.getProperty("CURRENCY"), curElement.getProperty("PRICE"));
        setMonetary(infoTags[i], amount, i != 22 && i != 14);
        switch(i) {
          case 13:
            setDefaultCurrency(amount);
            break;
          case 6:
            if (amount.getCurrencyType() == Currency.US_DOLLAR) {
              setMonetary("us_cur", amount, false);
              setString("currency", amount.fullCurrencyName());
            }
            setDefaultCurrency(amount);
            break;
          case 12:
            String optional = curElement.getProperty("OPTIONAL");
            setBoolean("insurance_optional", optional == null || (optional.equals("true")));
            break;
        }
        break;
      case 7:  //  Is a dutch auction?
      case 8:  //  Is a reserve auction?
      case 9:  //  Is a private auction?
      case 15: //  Fixed price
      case 17: //  PayPal accepted
        setBoolean(infoTags[i], true);
        if(i==7 || i==15) {
          String quant = curElement.getProperty("QUANTITY");
          if(quant == null) {
            setInteger("quantity", 1);
          } else {
            setInteger("quantity", Integer.parseInt(quant));
          }
        } else if(i==8) {
          setBoolean("reserve_met", "true".equals(curElement.getProperty("MET")));
        }
        break;
      case 19: //  Feedback score
        String feedback = curElement.getContents();
        if(mSeller == null) mSeller = new Seller();
        if(feedback != null) mSeller.setFeedback(Integer.parseInt(feedback));
        break;
      case 20: //  Positive feedback percentage (w/o the % sign)
        String percentage = curElement.getContents();
        if (mSeller == null) mSeller = new Seller();
        mSeller.setPositivePercentage(percentage);
        break;
      case 21: //  Seller info block
        mSeller = Seller.newFromXML(curElement);
        break;
      default:
        break;
        // commented out for FORWARDS compatibility.
        //        throw new RuntimeException("Unexpected value when handling AuctionInfo tags!");
    }
  }

  public void fromXML(XMLElement inXML) {
    super.fromXML(inXML);
    if(mSeller != null) mSeller.saveDB();
  }

  public XMLElement toXML() {
    XMLElement xmlResult = new XMLElement("info");

    addStringChild(xmlResult, "title");

    if(!getSellerName().equals("(unknown)") && mSeller != null) {
      XMLElement xseller = mSeller.toXML();
      xmlResult.addChild(xseller);
    }

    Date start = getStart();
    if(start != null) {
      XMLElement xstart = new XMLElement("start");
      xstart.setContents(Long.toString(start.getTime()));
      xmlResult.addChild(xstart);
    }

    Date end = getEnd();
    if(end != null) {
      XMLElement xend = new XMLElement("end");
      xend.setContents(Long.toString(end.getTime()));
      xmlResult.addChild(xend);
    }

    XMLElement xbidcount = new XMLElement("bidcount");
    xbidcount.setContents(Integer.toString(getNumBids()));
    xmlResult.addChild(xbidcount);

    XMLElement xinsurance = addCurrencyChild(xmlResult, "insurance");
    if(xinsurance != null) xinsurance.setProperty("optional", isInsuranceOptional() ?"true":"false");

    if(getCurBid() != null && !getCurBid().isNull()) {
      if (getCurBid().getCurrencyType() != Currency.US_DOLLAR) {
        addCurrencyChild(xmlResult, "usprice", Currency.US_DOLLAR);
      }
    }

    addCurrencyChild(xmlResult, "currently");
    addCurrencyChild(xmlResult, "shipping");
    addCurrencyChild(xmlResult, "buynow");
    addCurrencyChild(xmlResult, "buy_now_us");
    addCurrencyChild(xmlResult, "minimum");

    XMLElement xdutch = addBooleanChild(xmlResult, "dutch");
    if(xdutch != null) xdutch.setProperty("quantity", Integer.toString(getQuantity()));

    XMLElement xreserve = addBooleanChild(xmlResult, "reserve");
    if(xreserve != null) xreserve.setProperty("met", isReserveMet() ?"true":"false");

    addBooleanChild(xmlResult, "paypal");
    XMLElement xfixed = addBooleanChild(xmlResult, "fixed");
    if(xfixed != null && getQuantity() != 1) xfixed.setProperty("quantity", Integer.toString(getQuantity()));
    addBooleanChild(xmlResult, "private");

    addStringChild(xmlResult, "location");
    addStringChild(xmlResult, "highbidder");

    return xmlResult;
  }

  public void setThumbnail(String thumbPath) {
    if(thumbPath == null) setNoThumbnail(true);
    mThumbnailPath = thumbPath;
  }

  public void save() {
    String outPath = JConfig.queryConfiguration("auctions.savepath");
    if (outPath != null && outPath.length() != 0) {
      if (JConfig.queryConfiguration("store.auctionHTML", "true").equals("true")) {
        String filePath = outPath + System.getProperty("file.separator") + getIdentifier() + ".html.gz";

        if (mLoadedPage != null) {
          mLoadedPage.save(filePath);
        }
      }
    }
    mLoadedPage = null;
  }

  private GZip loadFile(File fp) {
    GZip localZip = new GZip();

    if(fp.exists()) {
      //  Okay, I don't allow loading auction data that's over 512K.  Duh.
      if(fp.length() < 512 * 1024) {
        try {
          JConfig.log().logDebug("Loading from backing page (file is " + fp.length() + " bytes)!");
          localZip.load(fp);
        } catch(IOException ioe) {
          JConfig.log().handleException("Couldn't read " + fp.getName(), ioe);
          return null;
        }

        return localZip;
      } else {
        JConfig.log().logDebug("Can't load " + fp.getName() + ", file is too large.");
      }
    }
    return null;
  }

  void setRealContent(StringBuffer changedContent, boolean final_data) {
    if(changedContent != null) {
      byte[] localBytes = changedContent.toString().getBytes();
      setRealContent(localBytes, final_data);
    }
  }

  void setRealContent(byte[] changedContent, boolean final_data) {
    String outPath = JConfig.queryConfiguration("auctions.savepath");

    if(changedContent != null) {
      mLoadedPage = new GZip();
      mLoadedPage.setData(changedContent);

      if(outPath != null && outPath.length() != 0) {
        if(final_data) {
          String filePath = outPath + System.getProperty("file.separator") + getIdentifier() + ".html.gz";
          mLoadedPage.save(filePath);
          mLoadedPage = null;
        }
      }
    }
  }

  GZip getRealContent() {
    File fp = JConfig.getContentFile(getIdentifier());
    if(fp != null) return loadFile(fp);
    return mLoadedPage;
  }

  public void setContent(StringBuffer inContent, boolean final_data) {
    setRealContent(inContent, final_data);
  }

  protected StringBuffer getContent() {
    StringBuffer sb;

    if(mLoadedPage != null) {
      StringBuffer outSB = mLoadedPage.getUncompressedData(false);
      if(outSB == null) outSB = new StringBuffer("mLoadedPage.getUncompressedData is null");
      sb = outSB;
    } else {
      JConfig.log().logDebug("mLoadedPage is null, returning the 'real' cached copy!");
      GZip gz = getRealContent();
      if(gz != null) {
        sb = gz.getUncompressedData();
        JConfig.log().logDebug("Turned the uncompressed data into a StringBuffer!");
        if(sb == null) JConfig.log().logDebug(" Failed to uncompress for id " + getIdentifier());
      } else {
        sb = new StringBuffer("Error getting real content.");
      }
    }
    return(sb);
  }

  public Object getServer() { return mServer; }

  public String getSellerName() {
    refreshSeller();
    return mSeller != null ? (mSeller.getSeller()) : "(unknown)";
  }

  private void refreshSeller() {
    if (mSeller == null) {
      String seller_id = get("seller_id");
      if (seller_id != null) mSeller = Seller.findFirstBy("id", seller_id);
    }
  }

  public Seller getSeller() {
    refreshSeller();
    return mSeller;
  }

  public String getPositiveFeedbackPercentage() {
    refreshSeller();
    if (mSeller != null) return mSeller.getPositivePercentage();
    return "n/a";
  }

  public int getFeedbackScore() {
    refreshSeller();
    if (mSeller != null) return mSeller.getFeedback();
    return 0;
  }

  protected void setSellerName(String sellerName) {
    if(sellerName == null || sellerName.length() == 0) return;

    if(mSeller == null) {
      mSeller = Seller.makeSeller(sellerName.trim());
    } else {
      mSeller = mSeller.makeSeller(sellerName, mSeller);
    }
    Integer seller_id = mSeller.getId();
    if(seller_id == null || seller_id == 0) {
      String raw_id = mSeller.saveDB();
      if (raw_id != null && raw_id.length() != 0) seller_id = Integer.parseInt(raw_id);
    }
    setInteger("seller_id", seller_id);
  }

  public void setIdentifier(String id) { setString("identifier", id); }
  public void setHighBidder(String highBidder) { setString("highBidder", highBidder); }
  protected void setTitle(String title) { setString("title", title); }
//  protected void setHighBidderEmail(String highBidderEmail) { setString("highBidderEmail", highBidderEmail); }
  protected void setItemLocation(String itemLocation) { setString("itemLocation", itemLocation); }

  protected void setInsuranceOptional(boolean insuranceOptional) { setBoolean("insuranceOptional", insuranceOptional); }
  protected void setFixedPrice(boolean fixedPrice) { setBoolean("fixedPrice", fixedPrice); }
  protected void setNoThumbnail(boolean noThumbnail) { setBoolean("noThumbnail", noThumbnail); }

  protected void setCurBid(Currency curBid) {       setMonetary("curBid", curBid); }
  protected void setMinBid(Currency minBid) {       setMonetary("minBid", minBid); }
  protected void setShipping(Currency shipping) {   setMonetary("shipping", shipping); }
  protected void setInsurance(Currency insurance) { setMonetary("insurance", insurance); }
  protected void setUSCur(Currency USCur) {         setMonetary("us_cur", USCur, false); }
  protected void setBuyNowUS(Currency buyNowUS) {   setMonetary("buy_now_us", buyNowUS, false); }
  protected void setBuyNow(Currency buyNow) {       setMonetary("buy_now", buyNow); }

  protected void setEnd(Date end) {
    if(end == null || end.equals(Constants.FAR_FUTURE)) {
      end = null;
    }
    setDate("end", end);
  }

  public void setNumBids(int numBids) { setInteger("numBids", numBids); }
  public void setPrivate(boolean isPrivate) { setBoolean("private", isPrivate); }

  protected void setPaypal(boolean paypal) { setBoolean("paypal", paypal); }
  protected void setEnded(boolean ended) { setBoolean("ended", ended); }

  public void setThumbnailURL(String url) {
    setNoThumbnail(false);
    potentialThumbnail = url;
  }

  public String getThumbnailURL() {
    if (potentialThumbnail != null) return potentialThumbnail;
    return getThumbnailById(getIdentifier());
  }

  public String getAlternateSiteThumbnail() {
    return getThumbnailById(getIdentifier() + "6464");
  }

  private static String getThumbnailById(String id) {
    return "http://thumbs.ebaystatic.com/pict/" + id + ".jpg";
  }

  private static Table sDB = null;
  protected static String getTableName() { return "auctions"; }
  protected Table getDatabase() { return getRealDatabase(); }
  private static Table getRealDatabase() {
    if (sDB == null) {
      sDB = openDB(getTableName());
    }
    return sDB;
  }

  public String saveDB() {
    //  Look for columns of type: {foo}_id
    //  For each of those, introspect for 'm{Foo}'.
    //  For each non-null of those, call 'saveDB' on it.
    //  Store the result of that call as '{foo}_id'.
    if(mSeller != null) {
      String seller_id = mSeller.saveDB();
      if(seller_id != null) set("seller_id", seller_id);
    }

    return super.saveDB();
  }

  public static AuctionInfo find(String id) {
    return findFirstBy("id", id);
  }

  public static AuctionInfo findFirstBy(String key, String value) {
    return (AuctionInfo) ActiveRecord.findFirstBy(AuctionInfo.class, key, value);
  }

  public static AuctionInfo findByIdentifier(String identifier) {
    return (AuctionInfo) ActiveRecord.findFirstBySQL(AuctionInfo.class, "SELECT * FROM auctions WHERE id IN (SELECT Max(id) FROM auctions WHERE identifier = '" + identifier + "')");
  }

  public static int count() {
    return ActiveRecord.count(AuctionInfo.class);
  }

  public static int uniqueCount() {
    return getRealDatabase().countBySQL("SELECT COUNT(DISTINCT(identifier)) FROM auctions WHERE identifier IS NOT NULL");
  }

  public static boolean deleteAll(List<Integer> toDelete) {
    if(toDelete.isEmpty()) return true;
    //  TODO - Replace with Guava?
    String auctions = buildCSL(toDelete);

    return getRealDatabase().deleteBy("id IN (" + auctions + ")");
  }

  public static List<AuctionInfo> findAllByIds(List<? extends Object> toFind) {
    if(toFind.isEmpty()) return new ArrayList<AuctionInfo>(0);

    String auctions = buildCSL(toFind);

    return (List<AuctionInfo>) ActiveRecord.findAllBySQL(AuctionInfo.class, "SELECT * FROM auctions WHERE id IN (" + auctions + ")");
  }

  private static String buildCSL(List<? extends Object> toDelete) {
    StringBuilder ids = new StringBuilder("");
    boolean first = true;
    for (Object id : toDelete) {
      if (!first) {
        ids.append(", ");
      }
      ids.append(id);
      first = false;
    }

    return ids.toString();
  }

  @SuppressWarnings({"unchecked"})
  public static List<AuctionInfo> findLostAuctions() {
    List<AuctionInfo> resultSet;
    try {
      resultSet = (List<AuctionInfo>) findAllBySQL(AuctionInfo.class, "SELECT * FROM auctions WHERE identifier NOT IN (SELECT DISTINCT(identifier) FROM entries)");
    } catch(Exception e) {
      JConfig.log().handleDebugException("Failed to find lost auctions.", e);
      resultSet = null;
    }
    return resultSet;
  }
}
