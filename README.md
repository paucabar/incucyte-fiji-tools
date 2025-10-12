# Incucyte Fiji Tools

This repository contains [Fiji (ImageJ)](https://fiji.sc) Groovy scripts designed to streamline the processing of image data exported from **Incucyte live-cell imaging microscopes**.

---

## Overview

### 1. `generate_stacks.groovy`
Generates image stacks from single-channel, single–time-point images exported by the Incucyte software.

- **Input:** Directory containing exported images.
- **Parameters:**
  - `Time interval (minutes)`: Set the time interval between frames in minutes.
- **Automatic detection:** The script automatically detects:
  - The available **channels** (e.g. Phase, Green, Red)
  - Whether the data correspond to a **time-lapse** or **single-time-point** acquisition.
- **Output:** Multidimensional image stacks corresponding to each field of view.

---

### 2. `generate_stacks_with_drift_correction.groovy`
Performs the same operation as above, but also applies **XY drift correction** to compensate for small position shifts across time points.

- **Input:** A multi-channel time-lapse image file.
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
See the Incucyte Export Guide for detailed instructions on how to export compatible TIFF files, including:

- Recommended file naming conventions
- Channel and format settings
- Consistent dimensions and bit depth
