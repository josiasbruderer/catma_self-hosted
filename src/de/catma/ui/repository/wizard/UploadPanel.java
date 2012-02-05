package de.catma.ui.repository.wizard;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;

import com.vaadin.ui.Alignment;
import com.vaadin.ui.Button;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Label;
import com.vaadin.ui.ProgressIndicator;
import com.vaadin.ui.Upload;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Button.ClickListener;
import com.vaadin.ui.Upload.FailedEvent;
import com.vaadin.ui.Upload.FailedListener;
import com.vaadin.ui.Upload.ProgressListener;
import com.vaadin.ui.Upload.Receiver;
import com.vaadin.ui.Upload.StartedEvent;
import com.vaadin.ui.Upload.StartedListener;
import com.vaadin.ui.Upload.SucceededEvent;
import com.vaadin.ui.Upload.SucceededListener;

import de.catma.CleaApplication;
import de.catma.core.document.source.TechInfoSet;

public class UploadPanel extends HorizontalLayout {

	private Upload upload;
	private ProgressIndicator pi;
	private Button btCancelUpload;
	private Label fileLabel;

	public UploadPanel() {
		initComponents();
		initActions();
	}

	private void initActions() {
		upload.addListener(new StartedListener() {
			
			public void uploadStarted(StartedEvent event) {
				pi.setValue(0f);
				pi.setVisible(true);
				pi.setPollingInterval(500);
				btCancelUpload.setVisible(true);
				fileLabel.setVisible(false);
			}
		});
		
		upload.addListener(new ProgressListener() {
			
			public void updateProgress(long readBytes, long contentLength) {
				pi.setValue(Float.valueOf(readBytes)/Float.valueOf(contentLength));
			}
		});
		upload.addListener(new FailedListener() {
			
			public void uploadFailed(FailedEvent event) {
				pi.setVisible(false);
				btCancelUpload.setVisible(false);
				fileLabel.setValue("Upload cancelled!");
				fileLabel.setVisible(true);
			}
		});
		
		upload.addListener(new SucceededListener() {
			
			public void uploadSucceeded(SucceededEvent event) {
				pi.setVisible(false);
				btCancelUpload.setVisible(false);
				fileLabel.setValue(event.getFilename() + " uploaded!");
				fileLabel.setVisible(true);
			}
		});
		
		upload.setReceiver(new Receiver() {
			
			public OutputStream receiveUpload(String filename, String mimeType) {
				try {
					String tempDir = ((CleaApplication)getApplication()).getTempDirectory();
					File uploadFile = new File(new File(tempDir), filename);
					if (uploadFile.exists()) {
						uploadFile.delete();
					}
			
					return new FileOutputStream(uploadFile);					
				} catch (FileNotFoundException e) {
					e.printStackTrace(); // TODO: handle
					return null;
				}
			}
		});
		
		btCancelUpload.addListener(new ClickListener() {
			
			public void buttonClick(ClickEvent event) {
				upload.interruptUpload();
				
			}
		});
				
	}

	private void initComponents() {
		setSpacing(true);
		upload = new Upload();
		upload.setButtonCaption("Upload local file");
		upload.setImmediate(true);
		addComponent(upload);

		pi = new ProgressIndicator();
		pi.setVisible(false);
		addComponent(pi);
		setComponentAlignment(pi, Alignment.MIDDLE_CENTER);
		
		btCancelUpload = new Button("Cancel");
		addComponent(btCancelUpload);
		btCancelUpload.setVisible(false);
		
		fileLabel = new Label();
		fileLabel.setVisible(false);
		addComponent(fileLabel);
		setComponentAlignment(fileLabel, Alignment.MIDDLE_CENTER);
		
	}

	public void addListener(StartedListener listener) {
		upload.addListener(listener);
	}

	public void addListener(FailedListener listener) {
		upload.addListener(listener);
	}

	public void addListener(SucceededListener listener) {
		upload.addListener(listener);
	}
	
	
	
}
