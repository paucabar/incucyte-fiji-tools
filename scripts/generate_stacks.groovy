#@ File(label="Select input directory", style="directory") myDir
#@ Integer(label="Time interval (minutes)", min=1, value=15) timeInterval

import ij.*
import ij.io.*
import ij.plugin.*
import ij.process.*
import ij.ImagePlus
import ij.ImageStack
import ij.CompositeImage
import ij.process.LUT
import java.awt.Color
import java.util.regex.*

// LUT Helpers
LUT grayscaleLUT() {
    byte[] r = new byte[256], g = new byte[256], b = new byte[256]
    for (int i = 0; i < 256; i++) r[i] = g[i] = b[i] = (byte)i
    return new LUT(r, g, b)
}

ImagePlus applyLutsToHyperStack(ImagePlus imp, List<String> channelNames) {
    if (imp == null || imp.getNChannels() != channelNames.size()) {
        println("Mismatch in LUT assignment: ${imp?.getNChannels()} vs ${channelNames.size()}")
        return imp
    }

    LUT[] luts = new LUT[channelNames.size()]
    channelNames.eachWithIndex { name, i ->
        switch (name) {
            case "Green":
                luts[i] = LUT.createLutFromColor(Color.GREEN)
                break
            case "Red":
                luts[i] = LUT.createLutFromColor(Color.RED)
                break
            default:
                luts[i] = grayscaleLUT()
        }
    }

    CompositeImage ci = new CompositeImage(imp, CompositeImage.COMPOSITE)
    ci.setLuts(luts)
    ci.setOpenAsHyperStack(true)
    ci.setDimensions(channelNames.size(), 1, imp.getNFrames())
    ci.updateAndDraw()
    return ci
}

// Folder Setup
def inputFolder = myDir
def outputFolder = new File(inputFolder.getParentFile(), "Stacks")
if (!outputFolder.exists()) outputFolder.mkdirs()

def availableChannels = ["Phase", "Green", "Red"].findAll { new File(inputFolder, it).exists() }
if (!availableChannels.contains("Phase")) {
    println("Phase folder is required. Exiting.")
    return
}
println("Detected channels: " + availableChannels)

// Collect Phase Files
def phaseFolder = new File(inputFolder, "Phase")
def phaseFiles = phaseFolder.listFiles().findAll { it.name.endsWith(".tif") }
if (phaseFiles.isEmpty()) {
    println("No Phase images found.")
    return
}

// Group by Key (FOV)
def keyPattern = ~/Phase_([A-Z]\d+_\d+)_\d{2}d\d{2}h\d{2}m/
def grouped = [:].withDefault { [] }

phaseFiles.each { f ->
    def m = f.name =~ keyPattern
    if (m.find()) grouped[m.group(1)] << f
}

if (grouped.isEmpty()) {
    println("No valid Phase file names matched expected pattern.")
    return
}

// Detect mode [Time-lapse, Single time-point] based on file count per group
def isTimelapse = grouped.values().any { it.size() > 1 }
println("Detected mode: " + (isTimelapse ? "Time-lapse" : "Single time-point"))

// Main Processing Loop
grouped.each { key, files ->
    files = files.sort { it.name }

    def imageListPerTime = []
    def width = 0
    def height = 0
    def dimsSet = false

    files.each { phaseFile ->
        def suffix = phaseFile.name.replaceFirst(/Phase_${key}/, "")
        def images = []

        availableChannels.eachWithIndex { ch, idx ->
            def chFile = new File(new File(inputFolder, ch), "${ch}_${key}${suffix}")
            if (!chFile.exists()) {
                println("Missing ${ch} file for $key$suffix")
                return
            }
            def imp = IJ.openImage(chFile.absolutePath)
            if (!imp) return

            if (!dimsSet) {
                width = imp.getWidth()
                height = imp.getHeight()
                dimsSet = true
            }

            def proc = imp.getBitDepth() == 16 ? imp.getProcessor().convertToByte(true) : imp.getProcessor()
            images << proc.getPixels()
            imp.close()
        }

        if (images.size() != availableChannels.size()) return
        imageListPerTime << images
    }

    if (!dimsSet || imageListPerTime.isEmpty()) {
        println("Skipping ${key} due to missing or incomplete data.")
        return
    }

    def numChannels = availableChannels.size()
    def numTime = imageListPerTime.size()
    def bitDepth = 8
    def hyperStack = IJ.createHyperStack(key, width, height, numChannels, 1, numTime, bitDepth)
    def stack = hyperStack.getStack()

    imageListPerTime.eachWithIndex { imgSet, t ->
        imgSet.eachWithIndex { pixels, c ->
            def index = hyperStack.getStackIndex(c + 1, 1, t + 1)
            stack.setPixels(pixels, index)
        }
    }

    hyperStack.setOpenAsHyperStack(true)
    hyperStack.setDimensions(numChannels, 1, numTime)

    def coloredStack = applyLutsToHyperStack(hyperStack, availableChannels)
    def cal = coloredStack.getCalibration()
    cal.frameInterval = timeInterval
    cal.setTimeUnit("minute")

    def outName = isTimelapse ? "Timelapse_${key}.tif" : "${key}.tif"
    def outFile = new File(outputFolder, outName)
    new FileSaver(coloredStack).saveAsTiff(outFile.absolutePath)
    println("Saved: " + outFile.absolutePath)

    coloredStack.close()
}

println("Processing complete.")
