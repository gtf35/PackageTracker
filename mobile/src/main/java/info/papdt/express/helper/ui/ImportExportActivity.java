package info.papdt.express.helper.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ShareCompat;
import android.support.v4.content.IntentCompat;
import android.support.v7.app.AlertDialog;
import android.view.View;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import info.papdt.express.helper.R;
import info.papdt.express.helper.dao.PackageDatabase;
import info.papdt.express.helper.model.Package;
import info.papdt.express.helper.support.FileUtils;
import info.papdt.express.helper.ui.common.AbsActivity;

public class ImportExportActivity extends AbsActivity implements View.OnClickListener {

	private PackageDatabase database;

	private static final String BACKUP_FILE_NAME = "PackageTrackerBackup_%s.json";

	public static final int REQUEST_WRITE_BACKUP_FILE = 10001, REQUEST_OPEN_FILE_RESTORE = 10002;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_import_export);

		mActionBar.setDisplayHomeAsUpEnabled(true);

		database = PackageDatabase.getInstance(getApplicationContext());
	}

	@Override
	protected void setUpViews() {
		$(R.id.action_backup_all_data).setOnClickListener(this);
		$(R.id.action_export_list).setOnClickListener(this);
		$(R.id.action_restore_all_data).setOnClickListener(this);
	}

	@Override
	@SuppressLint("SimpleDateFormat")
	public void onClick(View v) {
		switch (v.getId()) {
			case R.id.action_backup_all_data:
				Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
				intent.addCategory(Intent.CATEGORY_OPENABLE);
				intent.setType("text/*");

				Date date = Calendar.getInstance().getTime();
				SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd_HHmmss");

				intent.putExtra(
						Intent.EXTRA_TITLE,
						String.format(BACKUP_FILE_NAME, format.format(date))
				);
				startActivityForResult(intent, REQUEST_WRITE_BACKUP_FILE);
				break;
			case R.id.action_export_list:
				if (database.size() == 0) {
					Snackbar.make($(R.id.container), R.string.toast_list_empty, Snackbar.LENGTH_SHORT)
							.show();
				} else {
					new ShareTask().execute();
				}
				break;
			case R.id.action_restore_all_data:
				Intent intent1 = new Intent(Intent.ACTION_OPEN_DOCUMENT);
				intent1.addCategory(Intent.CATEGORY_OPENABLE);
				intent1.setType("*/*");
				startActivityForResult(intent1, REQUEST_OPEN_FILE_RESTORE);
				break;
		}
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == REQUEST_WRITE_BACKUP_FILE && resultCode == RESULT_OK) {
			if (data != null) {
				Uri uri = data.getData();
				new BackupTask().execute(uri);
			}
		}
		if (requestCode == REQUEST_OPEN_FILE_RESTORE && resultCode == RESULT_OK) {
			if (data != null) {
				Uri uri = data.getData();
				new RestoreTask().execute(uri);
			}
		}
	}

	public static void launch(Activity activity) {
		Intent intent = new Intent(activity, ImportExportActivity.class);
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		activity.startActivity(intent);
	}

	private class ShareTask extends AsyncTask<Void, Void, String> {

		@Override
		protected String doInBackground(Void... params) {
			StringBuilder sb = new StringBuilder();
			for (Package pack : database.getData()) {
				sb.append(
						getString(
								R.string.export_list_item_format,
								pack.name,
								pack.number + " " + pack.companyChineseName,
								(pack.data != null && pack.data.size() > 0)
										? pack.data.get(0).context
										: getString(R.string.item_text_cannot_get_package_status)
						)
				).append("\n\n");
			}
			sb.append(getString(R.string.export_list_end_text));
			return sb.toString();
		}

		@Override
		protected void onPostExecute(String text) {
			if (!isFinishing()) {
				Intent intent = new Intent(Intent.ACTION_SEND);
				intent.setType("text/plain");
				intent.putExtra(Intent.EXTRA_TEXT, text);
				startActivity(Intent.createChooser(intent, getString(R.string.dialog_share_title)));
			}
		}

	}

	private class BackupTask extends AsyncTask<Uri, Void, Boolean> {

		ProgressDialog progressDialog;
		Uri tempUri;

		@Override
		protected void onPreExecute() {
			progressDialog = new ProgressDialog(ImportExportActivity.this);
			progressDialog.setMessage(getString(R.string.dialog_backup_title));
			progressDialog.setCancelable(false);
			progressDialog.show();
		}

		@Override
		protected Boolean doInBackground(Uri... uri) {
			try {
				FileUtils.writeTextToUri(
						ImportExportActivity.this, tempUri = uri[0], database.getBackupData()
				);
				return true;
			} catch (Exception e) {
				e.printStackTrace();
				return false;
			}
		}

		@Override
		protected void onPostExecute(Boolean isSucceed) {
			if (!isFinishing()) {
				progressDialog.dismiss();
				if (isSucceed) {
					Snackbar.make(
							$(R.id.container), R.string.toast_backup_succeed, Snackbar.LENGTH_LONG
					).setAction(R.string.toast_backup_send_action, new View.OnClickListener() {
						@Override
						public void onClick(View v) {
							ShareCompat.IntentBuilder.from(ImportExportActivity.this)
									.addStream(tempUri)
									.setType("text/*")
									.startChooser();
						}
					}).show();
				} else {
					Snackbar.make(
							$(R.id.container), R.string.toast_backup_failed, Snackbar.LENGTH_LONG
					).show();
				}

			}
		}

	}

	private class RestoreTask extends AsyncTask<Uri, Void, Boolean> {

		ProgressDialog progressDialog;

		@Override
		protected void onPreExecute() {
			progressDialog = new ProgressDialog(ImportExportActivity.this);
			progressDialog.setMessage(getString(R.string.dialog_restoring_title));
			progressDialog.setCancelable(false);
			progressDialog.show();
		}

		@Override
		protected Boolean doInBackground(Uri... uri) {
			try {
				String fileData = FileUtils.readTextFromUri(ImportExportActivity.this, uri[0]);
				try {
					database.restoreData(fileData);
					database.save();
					return true;
				} catch (Exception e) {
					e.printStackTrace();
					return false;
				}
			} catch (IOException e) {
				return false;
			}
		}

		@Override
		protected void onPostExecute(Boolean isSucceed) {
			if (!isFinishing()) {
				progressDialog.dismiss();
				if (!isSucceed) {
					Snackbar.make(
							$(R.id.container), R.string.toast_restore_failed, Snackbar.LENGTH_LONG
					).show();
				} else {
					new AlertDialog.Builder(ImportExportActivity.this)
							.setTitle(R.string.dialog_restored_title)
							.setMessage(R.string.dialog_restored_message)
							.setCancelable(false)
							.setPositiveButton(R.string.dialog_restored_restart_button,
									new DialogInterface.OnClickListener() {
										@Override
										public void onClick(DialogInterface dialog, int which) {
											Intent intent = getPackageManager()
													.getLaunchIntentForPackage(getPackageName());
											ComponentName componentName = intent.getComponent();
											Intent i = IntentCompat
													.makeRestartActivityTask(componentName);
											startActivity(i);

											System.exit(0);
										}
									})
							.show();
				}
			}
		}

	}

}
