package com.infairy.tutorial.sdk.one;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Properties;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;

import com.infairy.cocina.SDK.debug.Debug;
import com.infairy.cocina.SDK.device.BundleDevice;
import com.infairy.cocina.SDK.device.BundleDeviceImpl;
import com.infairy.cocina.SDK.device.DevicePool;
import com.infairy.cocina.SDK.gene.EventTTSDNA;
import com.infairy.cocina.SDK.gene.InfairyInterface;
import com.infairy.cocina.SDK.gene.StatusImpl;
import com.infairy.cocina.SDK.gene.StatusInterface;
import com.infairy.cocina.SDK.property.Property;
import com.infairy.smarthome.tools.DBTools;
import com.infairy.smarthome.tools.Tools;


public class Main implements BundleActivator ,EventHandler {

	private static final String DEBUG = "debug";
	private static final String KEY_MOCK_STATUS = "KEY_MOCK_STATUS";
	private static final MockStatus[] MOCK_STATUS= new MockStatus[] {
			new MockStatus(25,60,"客廳"),
			new MockStatus(29,70,"廚房"),
			new MockStatus(27,65,"陽台"),
	};
	private BundleContext context;
	private BundleDevice bundleDevice;
	private DevicePool devicePool;
	private String bundleID;
	private Debug debug;
	private HashMap<String, MockStatus> mockStatus;
	private HashMap<String, String> sensorIDTag = new HashMap<String, String>();
	private Runnable readBundleResources = new Runnable() {
		
		@Override
		public void run() {
			// TODO Auto-generated method stub

			debug.println(Main.this, DEBUG, "開始從bundle讀取資料");
			//讀取bundle裡面的檔案
			InputStream is;
			try {
				is = context.getBundle().getResource("testFile.txt").openStream();
				
				BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(is));
				
				String line = null;
				while((line = bufferedReader.readLine()) != null) {
					debug.println(Main.this, "testFile.txt", line);
				}
								
				bufferedReader.close();				
				is.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			debug.println(Main.this, DEBUG, "結束從bundle讀取資料");
		}
	};
	
	private Runnable dbToolTutorial = new Runnable() {
		
		@Override
		public void run() {
			//製造假資料
			debug.println(Main.this, DEBUG, "開始製造資料");
			mockStatus = new HashMap<String, Main.MockStatus>();
			for (MockStatus status : MOCK_STATUS) {
				mockStatus.put(status.getArea(), status);
			}
			
			//存資料
			debug.println(Main.this, DEBUG, "開始儲存資料到SDCARD");
			saveMockStatus();
			debug.println(Main.this, DEBUG, "結束儲存資料到SDCARD");
			
			//讀資料
			debug.println(Main.this, DEBUG, "開始從SDCARD讀取資料");
			readMockStatus();
			debug.println(Main.this, DEBUG, "結束從SDCARD讀取資料");
			
		}
	};
	
	private Runnable deviceStatusTutorial = new Runnable() {

		@Override
		public void run() {
			//建立虛擬設備
			createAQISensorDevice();
			
			//儲存設備資料
			debug.println(Main.this, DEBUG, "開始儲存設備資料");
			for (MockStatus status : MOCK_STATUS) {
				saveHumidityStatus(status.getArea(), String.valueOf(status.getHumi()));
				saveTemperatureStatus(status.getArea(), String.valueOf(status.getTemp()));
			}
			debug.println(Main.this, DEBUG, "結束儲存設備資料");
			
			
			//讀取設備資料
			debug.println(Main.this, DEBUG, "開始讀取設備資料");
			StatusImpl[] status = devicePool.getAllStatus();
			for (int i = 0; i < status.length; i++) {

				debug.println(Main.this, "設備資料["+i+"]",status[i].toString() );
			}
			debug.println(Main.this, DEBUG, "結束讀取設備資料");			
			
		}
	
	};
	
	// 定義虛擬溫濕度Sensor元件
	private void createAQISensorDevice() {
		sensorIDTag.clear();
		String deviceid[] = bundleDevice.getBundleDeviceIDList(bundleID);

		//刪除虛擬設備
		if (deviceid != null) {
			for (int i = 0; i < deviceid.length; i++) {
				BundleDeviceImpl tmp = bundleDevice.getBundleDevice(deviceid[i]);

				if (tmp != null && tmp.GlobalKind.equals(getClass().getPackage().toString())) {
					bundleDevice.removeBundleDevice(deviceid[i]);
				}
			}
		}

		//新增虛擬設備
		for (String area:mockStatus.keySet()) {
				BundleDeviceImpl bdev = new BundleDeviceImpl();
				bdev.Object = Main.this.getClass();
				bdev.Alias = area;
				bdev.KIND = devicePool.DEVICE_KIND_SENSOR_MULTILEVEL;
				bdev.GlobalKind = getClass().getPackage().toString();
				bdev.CommandClass = new String[] { devicePool.SOFTWARE_CMDCLASS_SENSOR_MULTILEVEL };
				debug.println(this, "新建", area);
				String senID = bundleDevice.setBundleDevice(bundleID, bdev);

				devicePool.addDevice(this, senID);

				sensorIDTag.put(area, senID );
		}
	}

	
	private void saveTemperatureStatus(String area, String val) {
		
		String sensorid = sensorIDTag.get(area);
		
		if (sensorid == null)
			return;

		StatusImpl status;
		try {
			// 改變狀態
			status = new StatusImpl(sensorid);
			status.setData(StatusInterface.channel, "1");
			status.setData(StatusInterface.ValueType, devicePool.DEVICE_VALUE_TYPE_TEMPERATURE);
			status.setData(StatusInterface.DeviceAlias, "溫度[" + area + "]");
			status.setData(StatusInterface.Value, val);
			status.setData(StatusInterface.Unit, devicePool.DEVICE_VALUE_UNIT_DEGREE_C);
			status.setData(StatusInterface.TimeStamp, (System.currentTimeMillis() / 1000) + "");
			devicePool.addStatus(status);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private void saveHumidityStatus(String area, String val) {
		
		String sensorid = sensorIDTag.get(area);
		
		if (sensorid == null)
			return;

		StatusImpl status;
		try {
			// 改變狀態
			status = new StatusImpl(sensorid);
			status.setData(StatusInterface.channel, "2");
			status.setData(StatusInterface.ValueType, devicePool.DEVICE_VALUE_TYPE_HUMIDITY);
			status.setData(StatusInterface.DeviceAlias, "濕度[" + area + "]");
			status.setData(StatusInterface.Value, val);
			status.setData(StatusInterface.Unit, devicePool.DEVICE_VALUE_UNIT_Percent);
			status.setData(StatusInterface.TimeStamp, (System.currentTimeMillis() / 1000) + "");
			devicePool.addStatus(status);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private void readMockStatus() {
		Hashtable[] h = DBTools.readHashObject(Main.this, KEY_MOCK_STATUS);
		mockStatus = new HashMap<String, MockStatus>();
		if (h != null) {
			MockStatus temp;
			for (int i = 0; i < h.length; i++) {
				temp = new MockStatus((Float) h[i].get("temperature"), 
						(Float) h[i].get("humidity"),
						(String) h[i].get("area"));
				mockStatus.put(temp.getArea(), temp);
			}
		}
		debug.println(this, " readMockStatus ", "mockStatus size=" + mockStatus.size());
	}

	private void saveMockStatus() {

		Hashtable[] h = new Hashtable[mockStatus.size()];
		Iterator<MockStatus> it = mockStatus.values().iterator();

		DBTools.removeHashObject(Main.this, KEY_MOCK_STATUS);

		for (int i = 0; i < mockStatus.size(); i++) {
			if (!it.hasNext())
				break;
			MockStatus temp = it.next();
			h[i] = new Hashtable();
			h[i].put("humidity", temp.getHumi());
			h[i].put("temperature", temp.getTemp());
			h[i].put("area", temp.getArea());
			DBTools.saveHashObject(Main.this, KEY_MOCK_STATUS, h[i], true);
			debug.println(this, " saveMockStatus ", "mockStatus i=" + i);
		}

	}


	@Override
	public void start(BundleContext context) throws Exception {
		// TODO Auto-generated method stub
		this.context = context;
		
		
		Property property = (Property) Tools.getService(context, Property.class.getName(), "(Property=Setting)");
		debug = (Debug) Tools.getService(context, Debug.class.getName(), "(DEBUG=TELNET)");

		bundleDevice = (BundleDevice) Tools.getService(context, BundleDevice.class.getName(), "(DEVICE=BUNDLEDEVICE)");

		devicePool = (DevicePool) Tools.getService(context, DevicePool.class.getName(), property.getDeviceService());

		debug.println(this, DEBUG, "hi");
		
        /**
         * Register bundle
         */
		Properties props = new Properties();
		props.put(InfairyInterface.BundleAlias, "允飛應用教學1");
		boolean TF = devicePool.registerBundle(context, this, props);

		Hashtable<Object, Object> dict = new Hashtable<Object, Object>();
		dict.put(InfairyInterface.DeviceAlias, "允飛應用教學1"); // register device alias name which will show on all
															// UI.
		dict.put(InfairyInterface.DeviceGlobalKind, "test"); // Define the device's global kind for device's
																			// used.
		dict.put(InfairyInterface.DeviceLayoutDefined, InfairyInterface.LayoutTypeNone);

		bundleID = devicePool.addDevice(this, dict);

		//監聽TTS廣播
		devicePool.ListenBroadcast(context, this, devicePool.BROADCAST_CHANNEL_TTS);
		
		
		new Thread(new Runnable() {
			
			@Override
			public void run() {
				try {
					readBundleResources.run();
					dbToolTutorial.run();
					deviceStatusTutorial.run();
				}catch (Exception e) {
					// TODO: handle exception
					e.printStackTrace();
				}
			}
		}).start();
		
	}

	@Override
	public void stop(BundleContext context) throws Exception {
		// TODO Auto-generated method stub
		debug.println(this, DEBUG, "bey");
	}
	
	@Override
	public void handleEvent(Event event) {

		if (event == null)
			return;

		EventTTSDNA eto = (EventTTSDNA) event.getProperty(devicePool.BROADCAST_TTS_VOICE_TRIGGER_EVENTOBJECT);

		debug.println(this, DEBUG, "bid=" + eto.FROM_ID + " mid=" + eto.MESSAGE_ID);
		debug.println(this, DEBUG, "hour=" + eto.HOUR);
		
		for (int i = 0; i < eto.WORDS.length; i++) {
			String string = eto.WORDS[i];
			debug.println(this, DEBUG, "word["+i+"]= " + string);
			if (string.isEmpty())
				continue;
			
			if(devicePool.isInPinYinOf(string, "測試123") != -1) {
				debug.println(this, DEBUG, "輸入內容為: 測試123");
				devicePool.addTTS(Main.this, "收到測試123");
				break;
			}

		}

	}
	
	static class MockStatus {
		private float temp;
		private float humi;
		private String area;
		public MockStatus(float temp, float humi, String area) {
			super();
			this.temp = temp;
			this.humi = humi;
			this.area = area;
		}
		public float getTemp() {
			return temp;
		}
		public float getHumi() {
			return humi;
		}
		public String getArea() {
			return area;
		}
		
	}
}
