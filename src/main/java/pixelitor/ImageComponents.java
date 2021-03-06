/*
 * Copyright 2015 Laszlo Balazs-Csiki
 *
 * This file is part of Pixelitor. Pixelitor is free software: you
 * can redistribute it and/or modify it under the terms of the GNU
 * General Public License, version 3 as published by the Free
 * Software Foundation.
 *
 * Pixelitor is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Pixelitor. If not, see <http://www.gnu.org/licenses/>.
 */

package pixelitor;

import pixelitor.filters.comp.Crop;
import pixelitor.history.History;
import pixelitor.layers.ImageLayer;
import pixelitor.layers.Layer;
import pixelitor.menus.SelectionActions;
import pixelitor.menus.view.ZoomLevel;
import pixelitor.menus.view.ZoomMenu;
import pixelitor.tools.Tools;
import pixelitor.utils.ImageSwitchListener;
import pixelitor.utils.Messages;

import java.awt.Cursor;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Static methods for maintaining the list of open ImageComponent objects
 */
public class ImageComponents {
    private static final List<ImageComponent> icList = new ArrayList<>();
    private static ImageDisplay activeIC;
    private static final Collection<ImageSwitchListener> imageSwitchListeners = new ArrayList<>();

    private ImageComponents() {
    }

    public static void addIC(ImageComponent ic) {
        icList.add(ic);
    }

    public static boolean thereAreUnsavedChanges() {
        for (ImageComponent ic : icList) {
            if (ic.getComp().isDirty()) {
                return true;
            }
        }
        return false;
    }

    public static List<ImageComponent> getICList() {
        return icList;
    }

    private static void setNewImageAsActiveIfNecessary() {
        if (!icList.isEmpty()) {
            boolean activeFound = false;

            for (ImageComponent ic : icList) {
                if (ic == activeIC) {
                    activeFound = true;
                    break;
                }
            }
            if (!activeFound) {
                setActiveIC(icList.get(0), true);
            }
        }
    }

    public static ImageDisplay getActiveIC() {
        return activeIC;
    }

    public static Composition getActiveCompOrNull() {
        if (activeIC != null) {
            return activeIC.getComp();
        }

        return null;
    }

    public static Optional<Composition> getActiveComp() {
        if (activeIC != null) {
            return Optional.of(activeIC.getComp());
        }

        return Optional.empty();
    }

    public static Optional<Composition> findCompositionByName(String name) {
        return icList.stream()
                .map(ImageComponent::getComp)
                .filter(c -> c.getName().equals(name))
                .findFirst();
    }

    public static Layer getActiveLayerOrNull() {
        if (activeIC != null) {
            return activeIC.getComp().getActiveLayer();
        }

        return null;
    }

    public static Optional<Layer> getActiveLayer() {
        return getActiveComp().map(Composition::getActiveLayer);
    }

    public static ImageLayer getActiveImageLayerOrMaskOrNull() {
        if (activeIC != null) {
            Composition comp = activeIC.getComp();
            return comp.getActiveMaskOrImageLayerOrNull();
        }

        return null;
    }

    public static Optional<ImageLayer> getActiveImageLayerOrMask() {
        return getActiveComp().flatMap(Composition::getActiveMaskOrImageLayerOpt);
    }

    public static int getNrOfOpenImages() {
        return icList.size();
    }

    public static Optional<BufferedImage> getActiveCompositeImage() {
        return getActiveComp().map(Composition::getCompositeImage);
    }

    /**
     * Crops the active image based on the crop tool
     */
    public static void toolCropActiveImage(boolean allowGrowing) {
        try {
            Optional<Composition> opt = getActiveComp();
            opt.ifPresent(comp -> {
                Rectangle2D cropRect = Tools.CROP.getCropRect(comp.getIC());
                new Crop(cropRect, false, allowGrowing).process(comp);
            });
        } catch (Exception ex) {
            Messages.showException(ex);
        }
    }

    /**
     * Crops the active image based on the selection bounds
     */
    public static void selectionCropActiveImage() {
        try {
            getActiveComp().ifPresent(comp -> comp.getSelection().ifPresent(selection -> {
                Rectangle selectionBounds = selection.getShapeBounds();
                new Crop(selectionBounds, true, true).process(comp);
            }));
        } catch (Exception ex) {
            Messages.showException(ex);
        }
    }

    public static void imageClosed(ImageComponent ic) {
        icList.remove(ic);
        if (icList.isEmpty()) {
            onAllImagesClosed();
        }
        setNewImageAsActiveIfNecessary();
    }

    public static void setActiveIC(ImageDisplay display, boolean activate) {
        activeIC = display;
        if (activate) {
            if (display == null) {
                throw new IllegalStateException("cannot activate null imageComponent");
            }
            // activate is always false in unit tests
            ImageComponent ic = (ImageComponent) display;

            InternalImageFrame internalFrame = ic.getInternalFrame();
            Desktop.INSTANCE.activateInternalImageFrame(internalFrame);
            ic.onActivation();
        }
    }

    /**
     * When a new tool is activated, the cursor has to be changed for each image
     */
    public static void setToolCursor(Cursor cursor) {
        for (ImageComponent ic : icList) {
            ic.setCursor(cursor);
        }
    }

    public static void addImageSwitchListener(ImageSwitchListener listener) {
        imageSwitchListeners.add(listener);
    }

    private static void onAllImagesClosed() {
        setActiveIC(null, false);
        imageSwitchListeners.forEach(ImageSwitchListener::noOpenImageAnymore);
        History.onAllImagesClosed();
        SelectionActions.setEnabled(false, null);

        PixelitorWindow.getInstance().setTitle(Build.getPixelitorWindowFixTitle());
    }

    /**
     * Another image became active
     */
    public static void activeImageHasChanged(ImageComponent ic) {
        // not called in unit tests
        ImageComponent oldIC = (ImageComponent) activeIC;

        setActiveIC(ic, false);
        for (ImageSwitchListener listener : imageSwitchListeners) {
            listener.activeImageHasChanged(oldIC, ic);
        }

        Composition newActiveComp = ic.getComp();
        Layer layer = newActiveComp.getActiveLayer();
        AppLogic.activeLayerChanged(layer);

        SelectionActions.setEnabled(newActiveComp.hasSelection(), newActiveComp);
        ZoomMenu.zoomChanged(ic.getZoomLevel());

        AppLogic.activeCompSizeChanged(newActiveComp);
        PixelitorWindow.getInstance().setTitle(ic.getComp().getName() + " - " + Build.getPixelitorWindowFixTitle());
    }

    public static void newImageOpened(Composition comp) {
//        numFramesOpen++;
        imageSwitchListeners.forEach((imageSwitchListener) -> imageSwitchListener.newImageOpened(comp));
    }

    public static void repaintActive() {
        if (activeIC != null) {
            activeIC.repaint();
        }
    }

    public static void repaintAll() {
        //noinspection Convert2streamapi
        for (ImageComponent ic : icList) {
            ic.repaint();
        }
    }

    public static void fitActiveToScreen() {
        if (activeIC != null) {
            ((ImageComponent)activeIC).setupFitScreenZoomSize();
        }
    }

    public static void fitActiveToActualPixels() {
        if (activeIC != null) {
            activeIC.setZoom(ZoomLevel.Z100, false);
        }
    }

    /**
     * Called by keyboard shortcuts via the menu
     */
    public static void increaseZoomForActiveIC() {
        ZoomLevel currentZoom = activeIC.getZoomLevel();
        ZoomLevel newZoomLevel = currentZoom.zoomIn();
        activeIC.setZoom(newZoomLevel, false);
    }

    /**
     * Called by keyboard shortcuts via the menu
     */
    public static void decreaseZoomForActiveIC() {
        ZoomLevel currentZoom = activeIC.getZoomLevel();
        ZoomLevel newZoomLevel = currentZoom.zoomOut();
        activeIC.setZoom(newZoomLevel, false);
    }

    public static boolean isActive(ImageComponent ic) {
        return ic == activeIC;
    }

    public static boolean isActiveLayerImageLayer() {
        Layer activeLayer = activeIC.getComp().getActiveLayer();
        return activeLayer instanceof ImageLayer;
    }
}
