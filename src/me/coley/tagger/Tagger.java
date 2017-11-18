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
import java.nio.file.CopyOption;
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
import javafx.application.Application;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
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
	@CommandLine.Parameters(index = "2", description = "Comma separated extensions to use.", arity = "2..*")
	private String[] extensions;
	/**
	 * Whether to set the javafx stage as maximized or not.
	 */
	@CommandLine.Option(names = { "-m", "--max" }, description = "Maximize the program on start.")
	private boolean maximized;
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
		ensureDirectoriesExist();
		FilteredFiles files = new FilteredFiles(input);
		files.populate(extensions);
		files.checkForSave();
		// Setup java fx
		primaryStage.setTitle("Tagger");
		ImageView view = new ImageView(new Image(files.getNext()));
		StackPane root = new StackPane();
		root.getChildren().add(view);
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
	 * 
	 * @throws FileNotFoundException
	 */
	private void loadTagActionKeys() throws FileNotFoundException {
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
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * Ensures the input and output directories exist.
	 */
	private void ensureDirectoriesExist() {
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
		 * Save file name. If it exits it will be located in the
		 * {@link me.coley.tagger.Tagger#output output directory}.
		 */
		private static final String TAG_SAVE_FILE = "tags.json";
		/**
		 * Root directory to search in.
		 */
		private final File root;
		/**
		 * List of all files matching the {@link me.coley.tagger.Tagger#extensions
		 * approved extensions}.
		 */
		private final List<File> files = new ArrayList<>();
		/**
		 * Map of all files to their set of tags.
		 */
		private final Map<File, TagData> tags = new HashMap<>();
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
			tags.get(getCurrentFile()).toggle(tag);
		}

		class TagData {
			private final String baseName;
			private final String extension;
			private File file;
			private Set<String> tags = new HashSet<>();

			public TagData(File input) {
				String name = input.getName();
				this.file = new File(output, name);
				this.baseName = name.substring(0, name.lastIndexOf("."));
				this.extension = name.substring(baseName.length());
				try {
					Files.copy(Paths.get(input.getAbsolutePath()), Paths.get(file.getAbsolutePath()),
							StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
				} catch (IOException e) {
					fatal("Could not copy file to output directory: " + e.getMessage());
				}
			}

			public void toggle(String tag) {
				// Toggle tag status
				if (tags.contains(tag)) {
					tags.remove(tag);
				} else {
					tags.add(tag);
				}
				StringBuilder append = new StringBuilder("-");
				for (String part : tags) {
					append.append(part + "-");
				}
				File target = new File(output, baseName + append.toString().substring(0, append.length() - 1) + extension);
				try {
					Files.move(Paths.get(file.getAbsolutePath()), Paths.get(target.getAbsolutePath()),
							StandardCopyOption.REPLACE_EXISTING);
					System.out.println(file.getAbsolutePath());
					Files.deleteIfExists(Paths.get(file.getAbsolutePath()));
					file = target;
				} catch (IOException e) {
					fatal("Could not rename file when updating tags: " + e.getMessage());
				}
			}
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
						tags.put(file, new TagData(file));
					}
				}
			}
		}

		/**
		 * Checks if a save file containing tagging information from a previous session
		 * exists. If so the current tag map is updated.
		 */
		public void checkForSave() {
			File saveFile = new File(output, TAG_SAVE_FILE);
			// Check if exists, skip if not found.
			if (!saveFile.exists()) {
				return;
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