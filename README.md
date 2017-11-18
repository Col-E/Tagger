# Tagger

A simple program for organizing and tagging images. 

![Screenshot of tagging an image](example.png)

## Usage

**Executing the application jar**:
```
Usage: java -jar tagger.jar [-m] [-r] <input> <output> <extensions>...
      <input>                 Directory to parse.
      <output>                Directory to save to.
      <extensions>...
                              Comma separated extensions to use.
  -m, --max                   Maximize the program on start.
  -r, --resize                Resize images to fit to program dimensions.
```

**Example invoke with options and parameters**:
```
Example: java -jar tagger.jar -m images output png jpg jpeg
      Searches for images in the directory "images" with the extensions "png", "jpg", and "jpeg"
      Saves tagged images in the directory "output"
      Opens the program in maximized (-m) view
```

**Creating custom tags**:
Tags are stored in a JSON format. Running the program once will generate an example file for you. It should contain:
```
{
	"Q": "nature",
	"W": "architecture",
	"E": "painting"
}
```
The left hand side are the key names that java-fx uses. You can find a full list [here](https://docs.oracle.com/javafx/2/api/javafx/scene/input/KeyCode.html). On the left is the tag applied to the image.

**General usage**:
You should launch Tagger via command line in order to pass the required parameters. You *can technically* double click the jar to run it, but that is likely just going to crash and burn. See the example in the sections above for how to invoke via commmand line. Once the program is open it should display an image from the directory you gave. To navigate between images use the left and right arrow keys. Hit escape to close. Any other key can be updated in the config file to apply a tag. When you press a key the tag associated with that key is toggled for the current image. The current tags are displayed briefly when you press keys in the program and fade after a short bit.

Images are copied to the output folder given via command line. In this folder all copied images will have their original names until they are tagged. When an image has one or more tags it will show up as: `originalname__tag1-tag2-tag3.extension`. If you want to add a tag you do not have a keybind for, you need to close Tagger as it will not notice you have renamed the file. Opening Tagger again will parse all the tags from the file names, so you don't have to worry about restarting.