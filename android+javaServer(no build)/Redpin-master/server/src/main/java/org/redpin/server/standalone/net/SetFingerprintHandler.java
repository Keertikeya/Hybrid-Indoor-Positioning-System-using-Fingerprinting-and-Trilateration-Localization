/** *  Filename: SetFingerprintHandler.java (in org.redpin.server.standalone.net)
 *  This file is part of the Redpin project.
 * 
 *  Redpin is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published
 *  by the Free Software Foundation, either version 3 of the License, or
 *  any later version.
 *
 *  Redpin is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with Redpin. If not, see <http://www.gnu.org/licenses/>.
 *
 *  (c) Copyright ETH Zurich, Luba Rogoleva, Pascal Brogle, Philipp Bolliger, 2010, ALL RIGHTS RESERVED.
 * 
 *  www.redpin.org
 */
package org.redpin.server.standalone.net;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLDataException;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.redpin.server.standalone.core.Fingerprint;
import org.redpin.server.standalone.core.Location;
import org.redpin.server.standalone.core.Measurement;
import org.redpin.server.standalone.db.HomeFactory;
import org.redpin.server.standalone.db.homes.FingerprintHome;
import org.redpin.server.standalone.json.GsonFactory;
import org.redpin.server.standalone.net.Response.Status;
import org.redpin.server.standalone.svm.SVMSupport;
import org.redpin.server.standalone.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.*;

import com.google.gson.JsonElement;
import org.redpin.server.standalone.db.DatabaseConnection;

/**
 * @see IHandler
 * @author Pascal Brogle (broglep@student.ethz.ch)
 * @author Luba Rogoleva (lubar@student.ethz.ch)
 *
 */
public class SetFingerprintHandler  implements IHandler {

	private static final int INSTANT_TRAIN_THREASHOLD = 20;
	
	FingerprintHome fingerprintHome;
	
	public SetFingerprintHandler() {
		fingerprintHome = HomeFactory.getFingerprintHome();
	}
		
	/**
	 * @see IHandler#handle(JsonElement)
	 */
	@Override
	public Response handle(JsonElement data) {
		Response res;
		// know what content is there in the data.
		//System.out.println(data.toString());
		Log.getLogger().finer("data set: " + data);
		
		try{
		/*DatabaseConnection db=DatabaseConnection.getInstance();
		Connection conn = db.getConnection();
		Statement stmt = conn.createStatement();
        */
		List<String> queryList = new ArrayList<String>();
		
		JSONObject jsonObject = new JSONObject(data.toString());
		Log.getLogger().finer("json object set: " + jsonObject.toString());
	    JSONObject getlocation = jsonObject.getJSONObject("location");
	    JSONObject getmap = getlocation.getJSONObject("map");
	    JSONObject getmeasurement = jsonObject.getJSONObject("measurement");
	    
	    Object locationid = getlocation.get("id");
	    
	    Object mapid = getmap.get("id");
	    
	    JSONArray jsonArray = getmeasurement.getJSONArray("bluetoothReadings");
	    for (int i = 0; i < jsonArray.length(); i++) {
	        JSONObject explrObject = jsonArray.getJSONObject(i);
	        
	        String query = "Insert into bluetoothfingerprint (mapid, locid, rssi, bluetoothaddress, mapXCord, mapYCord)  values (" + getmap.get("id") +"," + getlocation.get("id") +"," + explrObject.get("majorDeviceClass") +",'" +  explrObject.get("bluetoothAddress").toString() + "'," +  getlocation.get("mapXcord") +"," + getlocation.get("mapYcord") + ");";
	        if(!queryList.contains(query)){
	        queryList.add(query);
	        }
	        
	    }
	    Log.getLogger().finer("queryList set: " + queryList.toString());
	    // Execute query code will go in this for loop
	    for (int i = 0; i < queryList.size(); i++) {
	    	System.out.println(queryList.get(i));
	    	DatabaseConnection db=DatabaseConnection.getInstance();
			Connection conn = db.getConnection();
			Statement stmt = conn.createStatement();
			int a = stmt.executeUpdate(queryList.get(i));
			System.out.println("btquery: "+ a);
		}
	    
		}catch(JSONException j){
			Log.getLogger().finer("Exception in JSON " + j);
		}catch( SQLException s){
		
			Log.getLogger().finer("Exception in SQL " + s);
		}
		
		Fingerprint fprint = GsonFactory.getGsonInstance().fromJson(data, Fingerprint.class);
		if (fprint.getLocation() != null && ((Location)fprint.getLocation()).getId() != null && ((Location)fprint.getLocation()).getId().intValue() != -1) {
			Location l = HomeFactory.getLocationHome().getLocation(((Location)fprint.getLocation()).getId(), null);
			fprint = new Fingerprint(l,(Measurement)fprint.getMeasurement());
		}
		fprint = fingerprintHome.add(fprint);
		
		if(fprint == null) {
			res = new Response(Status.failed, "could not add to database", null);
			Log.getLogger().fine("fingerpint could not be added to the database");
		} else {
			res = new Response(Status.ok, null, fprint);
			Log.getLogger().finer("fingerprint set: " + fprint);
			
			
			Location loc = (Location)fprint.getLocation();
			int count = fingerprintHome.getCount(loc);
			
			if (count < INSTANT_TRAIN_THREASHOLD) {
				Log.getLogger().fine("Training model (fp count for loc " + loc.getSymbolicID() + ": " + count);
				Thread trainer = new Thread(new Runnable() {				
					@Override
					public void run() {
						SVMSupport.train();					
					}
				});
				trainer.start();			
			}
			
		}
		
				
		
		return res;
	}

}
