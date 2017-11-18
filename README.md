# Tagger

A simple program for organizing and tagging images. 

## Usage

```
Usage: me.coley.tagger.Tagger [-m] <input> <output> <extensions>...
      <input>                 Directory to parse.
      <output>                Directory to save to.
      <extensions> ...
                              Comma separated extensions to use.
  -m, --max                   Maximize the program on start.
  
Example: me.coley.tagger.Tagger images output png jpg jpeg
      Searches for images in the directory "images" with the extensions "png", "jpg", and "jpeg"
	  Saves tagged images in the directory "output"
```