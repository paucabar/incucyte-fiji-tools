# Incucyte Fiji Tools

This repository contains [Fiji (ImageJ)](https://fiji.sc) Groovy scripts designed to streamline the processing of image data exported from **Incucyte live-cell imaging microscopes**.

---

## Overview

### 1. `generate_stacks.groovy`
Generates image stacks from single-channel, single–time-point images exported by the Incucyte software. Works with single- and multi-plate experiments in the same input folder.

- **Input:** `Raw/` directory containing `Phase/`, `Green/`, and/or `Red/` subfolders.
- **Parameters:**
  - `Time interval (minutes)`: Set the time interval between frames in minutes.
- **Automatic detection:** The script automatically detects:
  - The vessel ID (e.g. `VID9955`) from the exported filenames.
  - The available **channels** (e.g. Phase, Green, Red).
- **Output:** One multidimensional image stack per (vessel, well, FOV), named `VID9955_B3_1.tif`.

---

### 2. `generate_stacks_with_drift_correction.groovy`
Applies **XY drift correction** to compensate for small position shifts across time points.

- **Input:** A multi-channel time-lapse image file previously generated with the `generate_stacks.groovy` script.
- **Parameters:**
  - `Channel 1–3`: Choose the channels to include (`Red`, `Green`, `Phase`, or `None`)
  - `Reference`: Select which channel will be used to estimate drift.
- **Correction method:** Uses the [MultiStackReg](https://github.com/miura/MultiStackRegistration) plugin, which depends on the **BIG-EPFL** update site.

---

## Requirements

- **Fiji** (latest release recommended)
- Install the following update sites:
  - `MultiStackReg`
  - `BIG-EPFL`

To enable these:
1. Go to `Help > Update...`
2. Click `Manage update sites`
3. Check **MultiStackReg** and **BIG-EPFL**
4. Apply changes and restart Fiji.

---

## Notes & Limitations

- **Drift correction** works best when the **reference channel** contains **high-contrast, stationary features** (e.g. confluent cell layers or stable structures).
- Correction may **fail or behave erratically** when:
  - Cells are **not confluent** (too sparse).
  - Signal-to-noise is low.
  - Phase images have poor contrast.

---

## Example (Drift Correction in Action)

![](example_drift_correction.gif)

## Exporting Data from Incucyte

Correct export settings are essential for proper channel detection and time-lapse reconstruction.
A quick summary of the key steps is below:

In the **Incucyte Experiment Viewer**:

1. Click the **“Export images and movies”** icon.  
2. Under **Export Type**, select **“As Stored”**.  
3. Choose the **image type** (only one channel at a time):
   - **Phase:** only one option (8-bit)
   - **Red** and **Green:** choose **Uncalibrated (16-bit)** instead of calibrated  
4. Select the desired **scan times**, **wells**, and **fields of view** to export.  
5. The exported filenames must follow the Incucyte standard format, which includes the vessel ID automatically:
   ```
   VID9955_Phase_B3_1_00d00h00m.tif
   ```
   If your Incucyte version requires a manual prefix, set it to the channel name only (`Phase`, `Red`, or `Green`) — the vessel ID will be prepended automatically by the software.  
6. Save the files as **TIFF (.tif)**.  
7. Organize the exported files in the following folder structure:
- Images/
  - Raw/
    - Phase/
    - Green/
    - Red/

The `generate_stacks.groovy` script should be run using the `Raw` folder as input.  
All generated image stacks will be saved inside a new generated `Stacks` folder.
