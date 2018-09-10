/*   
 *   CATMA Computer Aided Text Markup and Analysis
 *   
 *   Copyright (C) 2009-2013  University Of Hamburg
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.catma.ui.repository.wizard;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.text.MessageFormat;

import com.vaadin.ui.Alignment;
import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Button.ClickListener;
import com.vaadin.v7.ui.HorizontalLayout;
import com.vaadin.v7.ui.Label;
import com.vaadin.v7.ui.ProgressIndicator;
import com.vaadin.ui.UI;
import com.vaadin.v7.ui.Upload;
import com.vaadin.v7.ui.Upload.FailedEvent;
import com.vaadin.v7.ui.Upload.FailedListener;
import com.vaadin.v7.ui.Upload.ProgressListener;
import com.vaadin.v7.ui.Upload.Receiver;
import com.vaadin.v7.ui.Upload.StartedEvent;
import com.vaadin.v7.ui.Upload.StartedListener;
import com.vaadin.v7.ui.Upload.SucceededEvent;
import com.vaadin.v7.ui.Upload.SucceededListener;

import de.catma.ui.CatmaApplication;
import de.catma.util.IDGenerator;

class UploadPanel extends HorizontalLayout {

	private Upload upload;
	private ProgressIndicator pi;
	private Button btCancelUpload;
	private Label fileLabel;
	private URI uploadedFileUri;

	public UploadPanel() {
		initComponents();
		initActions();
	}

	private void initActions() {
		upload.addStartedListener(new StartedListener() {
			
			public void uploadStarted(StartedEvent event) {
				pi.setValue(0f);
				pi.setVisible(true);
				pi.setPollingInterval(500);
				btCancelUpload.setVisible(true);
				fileLabel.setVisible(false);
			}
		});
		
		upload.addProgressListener(new ProgressListener() {
			
			public void updateProgress(long readBytes, long contentLength) {
				pi.setValue(Float.valueOf(readBytes)/Float.valueOf(contentLength));
			}
		});
		upload.addFailedListener(new FailedListener() {
			
			public void uploadFailed(FailedEvent event) {
				pi.setVisible(false);
				btCancelUpload.setVisible(false);
				fileLabel.setValue(Messages.getString("UploadPanel.uploadCancelled")); //$NON-NLS-1$
				fileLabel.setVisible(true);
			}
		});
		
		upload.addSucceededListener(new SucceededListener() {
			
			public void uploadSucceeded(SucceededEvent event) {
				pi.setVisible(false);
				btCancelUpload.setVisible(false);
				fileLabel.setValue(MessageFormat.format(Messages.getString("uploadedFeedback"), event.getFilename())); //$NON-NLS-1$
				fileLabel.setVisible(true);
			}
		});
		
		upload.setReceiver(new Receiver() {
			
			public OutputStream receiveUpload(String filename, String mimeType) {

				try {
					String tempDir = 
							((CatmaApplication)UI.getCurrent()).getTempDirectory();
					IDGenerator idGenerator = new IDGenerator();
					
					File uploadFile = new File(new File(tempDir), idGenerator.generate());
					uploadedFileUri = uploadFile.toURI();
					
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
		
		btCancelUpload.addClickListener(new ClickListener() {
			
			public void buttonClick(ClickEvent event) {
				upload.interruptUpload();
				
			}
		});
				
	}

	private void initComponents() {
		setSpacing(true);
		upload = new Upload();
		upload.setButtonCaption(Messages.getString("UploadPanel.uploadLocalFile")); //$NON-NLS-1$
		upload.setImmediate(true);
		addComponent(upload);

		pi = new ProgressIndicator();
		pi.setVisible(false);
		addComponent(pi);
		setComponentAlignment(pi, Alignment.MIDDLE_CENTER);
		
		btCancelUpload = new Button(Messages.getString("UploadPanel.Cancel")); //$NON-NLS-1$
		addComponent(btCancelUpload);
		btCancelUpload.setVisible(false);
		
		fileLabel = new Label();
		fileLabel.setVisible(false);
		addComponent(fileLabel);
		setComponentAlignment(fileLabel, Alignment.MIDDLE_CENTER);
		
	}

	public void addListener(StartedListener listener) {
		upload.addStartedListener(listener);
	}

	public void addListener(FailedListener listener) {
		upload.addFailedListener(listener);
	}

	public void addListener(SucceededListener listener) {
		upload.addSucceededListener(listener);
	}
	
	public URI getUploadedFileUri() {
		return uploadedFileUri;
	}
	
	
}