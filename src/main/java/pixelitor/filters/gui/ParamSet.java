/*
 * Copyright 2009-2014 Laszlo Balazs-Csiki
 *
 * This file is part of Pixelitor. Pixelitor is free software: you
 * can redistribute it and/or modify it under the terms of the GNU
 * General Public License, version 3 as published by the Free
 * Software Foundation.
 *
 * Pixelitor is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Pixelitor.  If not, see <http://www.gnu.org/licenses/>.
 */
package pixelitor.filters.gui;

import pixelitor.utils.IconUtils;

import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * A fixed set of GUIParam objects
 */
public class ParamSet implements Iterable<GUIParam> {
    private final List<GUIParam> paramList = new ArrayList<>();
    private ParamAdjustmentListener adjustmentListener;

    public ParamSet(GUIParam... params) {
        paramList.addAll(Arrays.asList(params));
    }

    public ParamSet(GUIParam param) {
        paramList.add(param);
    }

    public void addCommonActions(GUIParam... actions ) {
        if (paramList.size() > 1) {
            for (GUIParam action : actions) {
                if(action != null) {
                    paramList.add(action);
                }
            }
            addRandomizeAction();
            addResetAllAction();
        }
    }


    private void addRandomizeAction() {
        ActionParam randomizeAction = new ActionParam("Randomize Settings", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                randomize();
            }
        }, null);
        paramList.add(randomizeAction);
    }

    private void addResetAllAction() {
        ActionParam randomizeAction = new ActionParam("Reset All", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                reset();
            }
        }, IconUtils.getWestArrowIcon(), "Reset all settings to their default values.");
        paramList.add(randomizeAction);
    }

    @Override
    public Iterator<GUIParam> iterator() {
        return paramList.iterator();
    }

    /**
     * Resets all params without triggering an operation
     */
    public void reset() {
        for (GUIParam param : paramList) {
            param.reset(false);
        }
    }

    public void randomize() {
        for (GUIParam param : paramList) {
            param.randomize();
        }
    }

    public void startPresetAdjusting() {
        for (GUIParam param : paramList) {
            param.setDontTrigger(true);
        }
    }


    public void endPresetAdjusting(boolean trigger) {
        for (GUIParam param : paramList) {
            param.setDontTrigger(false);
        }
        if (trigger) {
            if (adjustmentListener != null) {
                adjustmentListener.paramAdjusted();
            }
        }
    }

    public void setAdjustmentListener(ParamAdjustmentListener listener) {
        adjustmentListener = listener;

        for (GUIParam param : paramList) {
            param.setAdjustmentListener(listener);
        }
    }

    public void considerImageSize(Rectangle bounds) {
        for (GUIParam param : paramList) {
            param.considerImageSize(bounds);
        }
    }
}