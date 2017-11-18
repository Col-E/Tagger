package me.coley.tagger;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.MalformedURLException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonValue;
import com.eclipsesource.json.ParseException;

import javafx.animation.FadeTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Duration;
import picocli.CommandLine;

/**
 * Simple program for organizing and tagging images.
 * 
 * @author Matt Coley
 * @since 11/17/2017
 */
public class Tagger extends Application implements Callable<Void> {
	private static final String TAG_KEYS = "tagkeys.json";
	private static final String DEFAULT_TAG_KEYS_CONTENT = "{\n\t\"Q\": \"nature\",\n\t\"W\": \"architecture\",\n\t\"E\": \"painting\"\n}";
	/**
	 * Root directory to search from.
	 */
	@CommandLine.Parameters(index = "0", description = "Directory to parse.")
	private String input;
	/**
	 * Root directory to save to.
	 */
	@CommandLine.Parameters(index = "1", description = "Directory to save to.")
	private String output;
	/**
	 * Array of extensions to allow during file searches.
	 */
	@CommandLine.Parameters(index = "2", description = "Comma separated extensions to use.", arity = "1..*")
	private String[] extensions;
	/**
	 * Whether to set the javafx stage as maximized or not.
	 */
	@CommandLine.Option(names = { "-m", "--max" }, description = "Maximize the program on start.")
	private boolean maximized;
	/**
	 * Whether to set the javafx stage as maximized or not.
	 */
	@CommandLine.Option(names = { "-r", "--resize" }, description = "Resize images to fit to program dimensions.")
	private boolean resize;
	/**
	 * Map of javafx KeyCodes to the tags they apply.
	 */
	private Map<KeyCode, String> keyToTag = new HashMap<>();

	public static void main(String[] args) {
		launch(args);
	}

	@Override
	public void start(Stage primaryStage) throws MalformedURLException, FileNotFoundException {
		// Setup parameters
		CommandLine.call(this, System.out, this.getParameters().getRaw().toArray(new String[0]));
		// Setup file and tag systems
		loadTagActionKeys();
		ensureProperInputs();
		FilteredFiles files = new FilteredFiles(input);
		files.populate(extensions);
		files.parseOutput();
		files.runInitialCopies();
		// Setup java fx ui
		log("Displaying...");
		primaryStage.setTitle("Tagger");
		ImageView view = new ImageView(new Image(files.getNext()));
		if (resize) {
			view.fitWidthProperty().bind(primaryStage.widthProperty());
			view.fitHeightProperty().bind(primaryStage.heightProperty());
		}
		Label notification = new Label("");
		notification.setStyle("-fx-font-size: 20px; -fx-padding: 5px; -fx-font-weight: bold; -fx-text-fill: #FFFFFF; -fx-effect: dropshadow(gaussian,rgba(0,0,0,1),2,0,1,1);");
		StackPane.setAlignment(notification, Pos.BOTTOM_LEFT);
		StackPane root = new StackPane();
		root.getChildren().addAll(view, notification);
		Scene scene = new Scene(root, 720, 576);
		scene.setOnKeyPressed(new EventHandler<KeyEvent>() {
			@Override
			public void handle(KeyEvent event) {
				KeyCode vk = event.getCode();
				try {
					// Move to next or previous image
					if (vk == KeyCode.RIGHT) {
						view.setImage(new Image(files.getNext()));
					} else if (vk == KeyCode.LEFT) {
						view.setImage(new Image(files.getPrevious()));
					}
					// Exit
					else if (vk == KeyCode.ESCAPE) {
						System.exit(0);
					}
					// Update tags
					else {
						files.toggle(keyToTag.get(vk));
					}
					// Display tags
					Platform.runLater(new Runnable() {
						@Override
						public void run() {
							// Fade notification and update with appropriate tags
							notification.setText(files.getTitle());
							FadeTransition ft = new FadeTransition(Duration.millis(3000), notification);
							ft.setFromValue(1.0);
							ft.setToValue(0.0);
							ft.setCycleCount(1);
							ft.play();
						}
					});
				} catch (MalformedURLException e) {
					err("Could not create path to file: " + e.getMessage());
				}
			}
		});
		primaryStage.setScene(scene);
		primaryStage.setMaximized(maximized);
		primaryStage.show();
	}

	/**
	 * Loads key-to-tag information from the config json.
	 */
	private void loadTagActionKeys() {
		File keyActionFile = new File(System.getProperty("user.dir") + File.separator + TAG_KEYS);
		if (!keyActionFile.exists()) {
			// If the key-to-action file does not exist, save an example file.
			// Notify the user and exit.
			try (Writer writer = new BufferedWriter(
					new OutputStreamWriter(new FileOutputStream(keyActionFile), "utf-8"))) {
				writer.write(DEFAULT_TAG_KEYS_CONTENT);
			} catch (IOException e) {
				fatal("Failed to save default configuration to '" + TAG_KEYS + "', reason: " + e.getMessage());
			}
			fatal("Could not find key-actions configuration. Generated a new one. Please fill it out and re-run the program.");
		}
		// Begin parsing
		try {
			log("Loaded tags {");
			JsonValue json = Json.parse(new String(Files.readAllBytes(Paths.get(keyActionFile.getAbsolutePath())),
					Charset.forName("utf-8")));
			json.asObject().forEach(m -> {
				keyToTag.put(KeyCode.getKeyCode(m.getName()), m.getValue().asString());
				log("\t" + m.getName() + ":" + m.getValue().asString());
			});
			log("}");
		} catch (IOException e) {
			fatal("Could not read from config file: " + keyActionFile.getAbsolutePath());
		} catch (ParseException e) {
			fatal("Syntax error in config file: [Line " + e.getLine() + ", Column " + e.getColumn() + "]");
		}
	}

	/**
	 * Ensures the parameters are correct.
	 */
	private void ensureProperInputs() {
		// Check if parameters are null. If so, quit.
		if (input == null ) {	
			fatal("You need to specify an input directory.");
		}
		if (output == null ) {	
			fatal("You need to specify an output directory.");
		}
		if (extensions == null ) {	
			fatal("You need to specify your extension types.");
		}
		// Check if in and output folders exist.
		File in = new File(input);
		File out = new File(output);
		if (!in.exists()) {
			in.mkdirs();
		}
		if (!out.exists()) {
			out.mkdirs();
		}
	}

	/**
	 * Print the given message and exit. It is more user friendly to see a single
	 * line of text than a massive exception trace with a message hidden somewhere
	 * in the spaghetti.
	 * 
	 * @param message
	 *            Message to print.
	 */
	private void fatal(String message) {
		err(message);
		System.exit(0);
	}

	/**
	 * Print the given message.
	 * 
	 * @param message
	 *            Message to print.
	 */
	private void err(String message) {
		System.err.println("ERROR: " + message);
	}

	/**
	 * Print the given message.
	 * 
	 * @param message
	 *            Message to print.
	 */
	private void log(String message) {
		System.out.println(message);
	}

	class FilteredFiles {
		/**
		 * Root directory to search in.
		 */
		private final File root;
		/**
		 * List of all files matching the {@link me.coley.tagger.Tagger#extensions
		 * approved extensions} in the {@link me.coley.tagger.Tagger#input input}
		 * directory.
		 */
		private final List<File> files = new ArrayList<>();
		/**
		 * Map of all files <i>(Original absolute paths)</i> to their set of tags.
		 */
		private final Map<String, TagData> tags = new HashMap<>();
		/**
		 * Current index in {@link #files}.
		 */
		private int index = -1;

		/**
		 * Construct FilteredFiles with given directory to use as root for searches.
		 * 
		 * @param dir
		 */
		public FilteredFiles(String dir) {
			root = new File(dir);
		}

		/**
		 * Retrieves the current file.
		 * 
		 * @return Current file.
		 */
		public File getCurrentFile() {
			return files.get(index);
		}

		/**
		 * Adds the given tag to the current image.
		 * 
		 * @param tag
		 *            Tag to apply.
		 */
		public void toggle(String tag) {
			// If invalid tag, do not apply
			if (tag == null) {
				return;
			}
			tags.get(getCurrentFile().getAbsolutePath()).toggle(tag);
		}

		/**
		 * 
		 * @return
		 */
		public String getTitle() {
			String tagArray = Arrays.toString(tags.get(getCurrentFile().getAbsolutePath()).tags.toArray());
			return "Tags: " + tagArray;
		}

		/**
		 * Retrieves next file in the list.
		 * 
		 * @return Next file as url.
		 * @throws MalformedURLException
		 *             Thrown if the url to the file could not be made.
		 */
		public String getNext() throws MalformedURLException {
			index++;
			if (index >= files.size()) {
				index = 0;
			}
			File value = getCurrentFile();
			return Paths.get(value.getAbsolutePath()).toUri().toURL().toString();
		}

		/**
		 * Retrieves previous file in the list.
		 * 
		 * @return Previous file as url.
		 * @throws MalformedURLException
		 *             Thrown if the url to the file could not be made.
		 */
		public String getPrevious() throws MalformedURLException {
			index--;
			if (index == -1) {
				index = files.size() - 1;
			}
			File value = files.get(index);
			return Paths.get(value.getAbsolutePath()).toUri().toURL().toString();
		}

		/**
		 * Populate internal file list with files containing any of the given
		 * extensions.
		 * 
		 * @param extensions
		 *            Approved extensions.
		 */
		public void populate(String[] extensions) {
			log("Populating image set...");
			Arrays.sort(extensions);
			populate(root, extensions);
		}

		/**
		 * Populate internal file list with files containing any of the given
		 * extensions.
		 * 
		 * @param dir
		 *            Directory to parse.
		 * @param extensions
		 *            Approved extensions.
		 */
		private void populate(File dir, String[] extensions) {
			for (File file : dir.listFiles()) {
				if (file.isDirectory()) {
					// Search sub-directories
					populate(file, extensions);
				} else {
					String name = file.getName();
					// Skip files without extensions
					if (!name.contains(".")) {
						continue;
					}
					// Check if extension is approved.
					String extension = name.substring(name.lastIndexOf(".") + 1).toLowerCase();
					if (Arrays.binarySearch(extensions, extension) > -1) {
						files.add(file);
						tags.put(file.getAbsolutePath(), new TagData(file));
					}
				}
			}
		}

		/**
		 * Checks the output directory for existing images. Tag information is then
		 * pulled from the file names and the current session's tag data is updated.
		 */
		public void parseOutput() {
			log("Scanning output directory for existing tag data...");
			File out = new File(output);
			File in = new File(input);
			// Check if exists, skip if not found.
			if (!out.exists()) {
				return;
			}
			// Iterate files in the output directory
			for (File file : out.listFiles()) {
				String name = file.getName();
				String originalName = name;
				String[] tags = new String[0];
				// Extract information from file name.
				if (name.contains(TagData.TAG_FILENAME_START)) {
					originalName = name.substring(0, name.indexOf(TagData.TAG_FILENAME_START))
							+ name.substring(name.lastIndexOf("."));
					tags = name.substring(
							name.lastIndexOf(TagData.TAG_FILENAME_START) + TagData.TAG_FILENAME_START.length(),
							name.lastIndexOf(".")).split(TagData.TAG_FILENAME_SPLIT);
				}
				// Check if the original file exists
				// If so, use it to update the data in the tags map.
				File original = new File(in, originalName);
				TagData data = this.tags.get(original.getAbsolutePath());
				if (data != null) {
					// Directly add to set since the method will attempt to move files around.
					for (String tag : tags) {
						data.tags.add(tag);
					}
					// Set init action to null. This prevents later execution of a purposeless
					// file-copy.
					data.initCopyAction = null;
					data.file = file;
				}
			}
		}

		/**
		 * Executes all remaining copy actions. Ensures files in the input directory
		 * have a presence in the output directory. If the action is null at this point,
		 * it has already been verified that it has a presence in the output directory.
		 */
		public void runInitialCopies() {
			log("Copying missing files to output directory...");
			for (TagData data : this.tags.values()) {
				if (data.initCopyAction != null) {
					data.initCopyAction.run();
				}
			}
		}

		/**
		 * Container for a file in the output directory with tag data.
		 * 
		 * @author Matt Coley
		 * @since 11/17/2017
		 */
		class TagData {
			/**
			 * Indicator in file name for beginning of tags.
			 */
			private final static String TAG_FILENAME_START = "__";
			/**
			 * Split between tags in the file name.
			 */
			private final static String TAG_FILENAME_SPLIT = "-";
			/**
			 * Name of the original file.
			 */
			private final String baseName;
			/**
			 * Extension of the original file.
			 */
			private final String extension;
			/**
			 * Current file. Updated as tags change the file's name when tags are
			 * {@link #toggle(String) toggled}.
			 */
			private File file;
			/**
			 * The set of tags the image has.
			 */
			private Set<String> tags = new HashSet<>();
			/**
			 * Action to copy the file in the input directory to the output directory. Put
			 * on hold in initialization and only executed if necessary.
			 */
			private Runnable initCopyAction;

			public TagData(File input) {
				String name = input.getName();
				this.file = new File(output, name);
				this.baseName = name.substring(0, name.lastIndexOf("."));
				this.extension = name.substring(baseName.length());
				this.initCopyAction = new Runnable() {
					@Override
					public void run() {
						try {
							// Copy input file to output directory.
							Files.copy(Paths.get(input.getAbsolutePath()), Paths.get(file.getAbsolutePath()),
									StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
						} catch (IOException e) {
							fatal("Could not copy file to output directory: " + e.getMessage());
						}
					}
				};
			}

			public void toggle(String tag) {
				// Toggle tag status
				if (tags.contains(tag)) {
					tags.remove(tag);
				} else {
					tags.add(tag);
				}
				// Create text to append to file name (of the tags)
				StringBuilder append = new StringBuilder();
				if (tags.size() > 0) {
					append.append(TAG_FILENAME_START);
				}
				for (String part : tags) {
					append.append(part + TAG_FILENAME_SPLIT);
				}
				// Create new file
				String ap = append.length() == 0 ? "" : append.toString().substring(0, append.length() - 1);
				File target = new File(output, baseName + ap + extension);
				try {
					// Move existing file to new file.
					// Set file to new file.
					Files.move(Paths.get(file.getAbsolutePath()), Paths.get(target.getAbsolutePath()),
							StandardCopyOption.REPLACE_EXISTING);
					file = target;
				} catch (IOException e) {
					fatal("Could not rename file when updating tags: " + e.getMessage());
				}
			}
		}
	}

	/**
	 * Picocli required. Not used due to javafx's required method of instantiation.
	 */
	@Override
	public Void call() throws Exception {
		return null;
	}
}