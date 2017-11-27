package de.nilsbeck.notenspiegel;

import android.app.*;
import android.content.*;
import android.content.res.*;
import android.net.*;
import android.os.*;
import android.util.*;
import android.view.*;
import android.view.View.*;
import android.webkit.*;
import android.widget.*;
import java.io.*;
import java.net.*;
import android.support.v4.content.*;

public class MainActivity extends Activity 
{
   	ProgressDialog mProgressDialog;
	public static final String PREFS = "settings";
	public static final String PWD = "password";
	public static final String USR = "username";
	public static final String STD = "studiengang";
	Activity activity;

	@Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
   
		activity=this;
		// Locate the Buttons in activity_main.xml
		final EditText usernameEtxt = (EditText) findViewById(R.id.usernameEdit);
		final EditText passwordEtxt = (EditText) findViewById(R.id.passwordEdit);
		final EditText studiengangEtxt = (EditText) findViewById(R.id.studiengangEdit);
		Button loadPdf = (Button) findViewById(R.id.loadBtn);
		Button saveBtn = (Button) findViewById(R.id.saveBtn);
	
		loadStoredData(usernameEtxt,passwordEtxt,studiengangEtxt);
        
		// Capture button click
		loadPdf.setOnClickListener(new OnClickListener() {
				public void onClick(View arg0) {
					String html= loadContentFromFile(getBaseContext(), "loadpdf.html");
					html = html.replace("{USERNAME}",usernameEtxt.getText()).replace("{PASSWORD}", passwordEtxt.getText())
							.replace("{STUDIENGANG}", studiengangEtxt.getText());
					WebView _webView = (WebView) findViewById(R.id.view);
					_webView.setWebViewClient(new WebViewClient(){
							@SuppressWarnings("deprecation")
							@Override
							public boolean shouldOverrideUrlLoading(WebView view, String url) {
								if (url.indexOf("https://vorlesungen.tu-bs.de/qisserver/rds?state=verpublish&status=init&vmfile=no&publishid=0&moduleCall=myNews&publishConfFile=myReports&publishSubDir=reports&breadCrumbSource=news&topitem=functions&startpage=portal.vm&chco=y") > -1) {
									return true;
								}
								else if(url.contains("hisreports")){
									Log.d("app","url: "+url);
									new DownloadPdfTask(MainActivity.this).execute(url);
									view.setVisibility(View.INVISIBLE);
								}	
								return false;
							}
						});
					WebSettings settings = _webView.getSettings();
					settings.setJavaScriptEnabled(true);
					settings.setAllowFileAccessFromFileURLs(true); //Maybe you don't need this rule
					settings.setAllowUniversalAccessFromFileURLs(true);
					settings.setDomStorageEnabled(true);
					_webView.setWebChromeClient(new WebChromeClient(){
						private static final String TAG="WVDebugger";
					
						@Override
						public boolean onConsoleMessage(ConsoleMessage m){
							Log.d(TAG,m.lineNumber()+":"+m.message()+"-"+m.sourceId());
							return true;
						}
					});
					_webView.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "utf-8", "");
					_webView.setVisibility(View.VISIBLE);
				}
			});
			
		saveBtn.setOnClickListener(new OnClickListener() {
			public void onClick(View arg0){
				//private prefs to store password etc
				//data backup is turned off in android manifest
				//password could only be obfuscated to be stored more securely,but
				//root access will allow reading all necesary data ultimately.
				//only a server based system is secure but not wanted in this case
				//just do not save your password on rooted devices!!
				SharedPreferences.Editor editor = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
				editor.putString(USR, usernameEtxt.getText().toString());
				editor.putString(PWD, passwordEtxt.getText().toString());
				editor.putString(STD, studiengangEtxt.getText().toString());
				editor.apply();
			}
		});
	}

	private void loadStoredData(EditText usernameEtxt, EditText passwordEtxt, EditText studiengangEtxt)
	{
		SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE); 
		
			usernameEtxt.setText(prefs.getString(USR, "Keine Angabe"));
			passwordEtxt.setText(prefs.getString(PWD, ""));
			studiengangEtxt.setText(prefs.getString(STD, ""));
		
	}
		
	public static InputStream loadInputStreamFromAssetFile(Context context, String fileName){
		AssetManager am = context.getAssets();
		try {
			InputStream is = am.open(fileName);
			return is;
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	public static String loadContentFromFile(Context context, String path){
		String content = null;
		try {
			InputStream is = loadInputStreamFromAssetFile(context, path);
			int size = is.available();
			byte[] buffer = new byte[size];
			is.read(buffer);
			is.close();
			content = new String(buffer, "UTF-8");
		} catch (IOException ex) {
			ex.printStackTrace();
			return null;
		}
		return content;
	}

	public byte[] readBytes(InputStream inputStream) throws IOException {
		// this dynamically extends to take the bytes you read
		ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();

		// this is storage overwritten on each iteration with bytes
		int bufferSize = 1024;
		byte[] buffer = new byte[bufferSize];

		// we need to know how may bytes were read to write them to the byteBuffer
		int len = 0;
		while ((len = inputStream.read(buffer)) != -1) {
			byteBuffer.write(buffer, 0, len);
		}

		// and then we can return your byte array.
		return byteBuffer.toByteArray();
	}
	
	private class DownloadPdfTask extends AsyncTask<String, Void, byte[]> {
		private ProgressDialog dialog;

		public DownloadPdfTask(MainActivity activity) {
			dialog = new ProgressDialog(activity);
		}

		protected void onPreExecute() {
            this.dialog.setMessage("PDF wird geladen...");
            this.dialog.show();
        }

		protected byte[] doInBackground(String... urls) {
			byte[] bytes =null;
			try
			{
				InputStream input = new URL(urls[0]).openStream();
				bytes = readBytes(input);
			}
			catch(Exception ex){
				ex.printStackTrace();
			}
			return bytes;
		}

		protected void onPostExecute(byte[] result) {
			if (dialog.isShowing()) {
				dialog.dismiss();
			}
			if(result!=null) {
				File gradesPath = new File(getBaseContext().getCacheDir(), "grades");
				gradesPath.mkdir();
				File newFile = new File(gradesPath, "notenspiegel.pdf");
				try
				{
					//override file if already exists, save internally in cache directory
					BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(newFile, false));
					bos.write(result);
					bos.flush();
					bos.close();
					Uri contentUri = FileProvider.getUriForFile(getBaseContext(), "de.nilsbeck.fileprovider", newFile);
					Intent intent = new Intent(Intent.ACTION_VIEW);
					
					//FilePrivider is used to allow file access
					intent.setDataAndType(contentUri, "application/pdf");
					intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
					startActivity(intent);
				}
				catch (FileNotFoundException e)
				{
					Toast.makeText(getBaseContext(),"Etwas ist schief gelaufen :(", Toast.LENGTH_SHORT).show();
				}
				catch(IOException ex){
					Toast.makeText(getBaseContext(),"Etwas ist schief gelaufen :(", Toast.LENGTH_SHORT).show();
				}
			}
			else Toast.makeText(getBaseContext(),"Etwas ist schief gelaufen :(", Toast.LENGTH_SHORT).show();
		}	
	}
}
