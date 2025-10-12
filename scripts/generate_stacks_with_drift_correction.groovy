#@ File(label="Select image", style="file") inputFile
#@ String (label="Channel 1", choices={"None", "Red", "Green", "Phase"}, style="listBox") channel1
#@ String (label="Channel 2", choices={"None", "Red", "Green", "Phase"}, style="listBox") channel2
#@ String (label="Channel 3", choices={"None", "Red", "Green", "Phase"}, style="listBox") channel3
#@ String (label="Reference", choices={"Red", "Green", "Phase"}, style="radioButtonHorizontal") referenceName

import ij.IJ
import java.io.File
import ij.io.Opener
import ij.ImagePlus
import ij.plugin.Duplicator
import ij.plugin.ContrastEnhancer
import ij.plugin.ChannelSplitter
import ij.plugin.RGBStackMerge
import ij.process.LUT
import java.awt.Color

// Open image file as an ImagePlus
ImagePlus importImage (File inputFile) {
	String imagePath = inputFile.getAbsolutePath()
	def opener = new Opener()
	opener.useHandleExtraFileTypes = true
	String extension = imagePath[imagePath.lastIndexOf('.')+1..-1]
	println "Importing ${inputFile.getName()} image. The image is a ${extension.toUpperCase()} file"
	ImagePlus imp = opener.openUsingBioFormats(imagePath)
	return imp
}

// Create output folder
String createOutputFolder(File inputFile, String name) { 
    File outDir = new File(inputFile.getParentFile(), name)
    if (outDir.getParentFile() != null) {
        outDir.mkdirs() // Create directories if they don't exist
    }
    outDir.createNewFile() // Create the file itself
    return outDir.getAbsolutePath() // Return the absolute path of the folder
}

// Create a grayscale LUT that transitions from black to white
LUT grayscaleLUT() {
	int size = 256 // Number of entries in the LUT
	byte[] r = new byte[size]
	byte[] g = new byte[size]
	byte[] b = new byte[size]
	
	for (int i = 0; i < size; i++) {
	    r[i] = (byte) i
	    g[i] = (byte) i
	    b[i] = (byte) i
	}
	
	LUT lut = new LUT(r, g, b)
	return lut
}

// Update the LUTs of a multi-channel image according to the channel names set by the user
void updateLuts(ImagePlus imp, List channelNames) {
	if (imp.getNChannels() < 2) {
		println "Error: The merged image should be a multi-channel image, but has ${imp.getNChannels()} channels."
    	return // Stop the script
	}
	
	// set composite LUTs
	LUT[] luts = imp.getLuts()
	
	if (luts.size() != channelNames.size()) {
		println "Error: The image LUTS (${luts.size()}) and channels (${channelNames.size()}) do not match."
    	return // Stop the script
	}
	
	channelNames.eachWithIndex { channel, index ->
        switch (channel) {
            case "Red":
                luts[index] = LUT.createLutFromColor(Color.RED)
                break
            case "Green":
                luts[index] = LUT.createLutFromColor(Color.GREEN)
                break
            default:
                luts[index] = grayscaleLUT()
        }
	}
	imp.setLuts(luts)
	imp.updateAndDraw()
	return
}

// Update the LUT of a single-channel image according to the channel name set by the user
void updateLut(ImagePlus imp, List channelNames) {
	if (imp.getNChannels() > 1) {
		println "Error: The  image should be a single-channel image, but has ${imp.getNChannels()} channels."
    	return // Stop the script
	}
	
	// set image LUT
	LUT[] luts = imp.getLuts()
	
	if (luts.size() != channelNames.size()) {
		println "Error: The image LUTS ${luts.size()} and channels ${channelNames.size()} do not match."
    	return // Stop the script
	}
	
	channelNames.eachWithIndex { channel, index ->
        switch (channel) {
            case "Red":
                luts[index] = LUT.createLutFromColor(Color.RED)
                break
            case "Green":
                luts[index] = LUT.createLutFromColor(Color.GREEN)
                break
            default:
                luts[index] = grayscaleLUT()
        }
	}
	imp.setLut(luts[0])
	imp.updateAndDraw()
	return
}

// Close all open images
IJ.run("Close All")

// Check choices
def channelNames = [] // Declare as a List
String[] channelChoices = [channel1, channel2, channel3]

channelChoices.each { choice ->
    if (choice != "None") {
        channelNames << choice // Append to the list
    }
}

if (channelChoices.size() != channelNames.size()) {
	println "Channel names have been sorted: user input ${channelChoices} vs sorted names ${channelNames}."
}

// Check for repeated names
if (channelNames.size() != channelNames.toSet().size()) {
    println "Error: Duplicate channel names found. This is not possible."
    return // Stop the script
}

// Check if the reference channel is valid
if (!channelNames.contains(referenceName)) {
    println "Error: The reference channel '${referenceName}' must be one of the selected channel names: ${channelNames}."
    return // Stop the script
}

// Find the position of the reference in channelNames
int referenceChannel = channelNames.indexOf(referenceName)

println "Channel names are valid: ${channelNames}."
println "Reference channel '${referenceName}' is valid and is found at position ${referenceChannel} in channelNames."


// Load and display the image
def imp = importImage(inputFile)
imp.show()
if (imp == null) {
    println("No image selected. Exiting.")
    return // Stop the script
}

// Check dimensions
int channels = imp.getNChannels()
int frames = imp.getNFrames()
int slices = imp.getNSlices()

if (channels != channelNames.size()) {
    println("Error: the detected channels ${channels} do not match the specified channels (${channelNames.size()}).")
    return // Stop the script
}

if (frames == 1 && slices == 1) {
	println("The image must be 3D (more than one frame (t) or slice (z)).")
	return // Stop the script
}

// Create required directories if they don't exist
String dir = IJ.getDirectory("image")
String name = imp.getTitle()
String nameWithoutExtension = name.lastIndexOf(".") >= 0 ? name[0..<name.lastIndexOf(".")] : name
String transFile = "TransformationFile_" + nameWithoutExtension + ".txt"
String transFileDir = createOutputFolder (inputFile, "Transformation Files")
String outputDir = createOutputFolder (inputFile, "Aligned")

// Split channels
ImagePlus[] channelImages = []
if (channels > 1) {
	channelImages = ChannelSplitter.split(imp)
	
	if (channelImages.size() != channels) {
	    println("Failed to split channels.")
	    return // Stop the script
	}

	// Hide the image and show the split channels
	imp.hide()
	for (int i = 0; i < channelImages.size(); i++) {
	    channelImages[i].show()
	}
	
	// Display as tiles
	IJ.run("Tile", "");

} else {
	channelImages = [imp] // Initialize with the single ImagePlus
}


// Perform MultiStackReg on reference channel and save the transformation file
String title = channelImages[referenceChannel].getTitle()
if (referenceName != "Phase") {
	println("Aligning $title '${channelNames[referenceChannel]}' stack and generating the transformation file.")
	IJ.run(channelImages[referenceChannel], "MultiStackReg", "stack_1=" + title +
	    " action_1=Align file_1=[" + transFileDir + File.separator + transFile + "]" +
	    " stack_2=None action_2=Ignore file_2=[] transformation=[Rigid Body] save")
} else {
	println("Preprocessing $title 'Phase' channel for registration.")
	// duplicate phase stack
	def dup = new Duplicator()
	ImagePlus refDuplicated = dup.run(channelImages[referenceChannel]) //duplicate the reference channel stack

	// normalize
	double saturated = 0.35
	ContrastEnhancer ce = new ContrastEnhancer()
	ce.setNormalize(true)
	ce.setProcessStack(true)
	ce.setUseStackHistogram(true)
	ce.stretchHistogram(refDuplicated, saturated)
	
	// find edges
	IJ.run(refDuplicated, "Find Edges", "stack")

	// Perform MultiStackReg on the preprocessed image
	title = refDuplicated.getTitle()
	refDuplicated.show()

	// Display as tiles
	IJ.run("Tile", "");
	
	IJ.run(refDuplicated, "MultiStackReg", "stack_1=" + title +
	    " action_1=Align file_1=[" + transFileDir + File.separator + transFile + "]" +
	    " stack_2=None action_2=Ignore file_2=[] transformation=[Rigid Body] save")
	refDuplicated.hide()
	refDuplicated.close()
}

// Apply the transformation to the rest of the channels using the saved transformation file
channelImages.eachWithIndex { stack, index ->
	if (index != referenceChannel || referenceName == "Phase") {
		title = channelImages[index].getTitle()
		println("Aligning $title '${channelNames[index]}' channel.")
		IJ.run(channelImages[index], "MultiStackReg", "stack_1=" + title +
		    " action_1=[Load Transformation File] file_1=[" + transFileDir + File.separator + transFile + "]" +
		    " stack_2=None action_2=Ignore file_2=[] transformation=[Rigid Body]")
	}
}

channelImages.each { C ->
	C.hide()
}

// Merge channels back together and save
if (channels > 1) {
	ImagePlus mergedImage = RGBStackMerge.mergeChannels(channelImages, false)
	if (mergedImage != null) {
	    updateLuts(mergedImage, channelNames)
	    IJ.saveAs(mergedImage, "Tiff", outputDir + File.separator + name)
	    mergedImage.close()
	} else {
	    println("Failed to merge channels.")
	}
} else {
	updateLut(channelImages[0], channelNames)
	IJ.saveAs(channelImages[0], "Tiff", outputDir + File.separator + name)
}

// Cleanup
channelImages.each { it.close() }

return // Stop the script
