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
package pixelitor.tools.shapestool;

import org.jdesktop.swingx.combobox.EnumComboBoxModel;
import org.jdesktop.swingx.painter.effects.AreaEffect;
import pixelitor.Composition;
import pixelitor.ImageComponent;
import pixelitor.ImageComponents;
import pixelitor.filters.gui.RangeParam;
import pixelitor.filters.painters.EffectsPanel;
import pixelitor.history.History;
import pixelitor.history.NewSelectionEdit;
import pixelitor.history.PixelitorEdit;
import pixelitor.history.SelectionChangeEdit;
import pixelitor.layers.ImageLayer;
import pixelitor.selection.Selection;
import pixelitor.tools.ClipStrategy;
import pixelitor.tools.ShapeType;
import pixelitor.tools.ShapesAction;
import pixelitor.tools.StrokeType;
import pixelitor.tools.Tool;
import pixelitor.tools.ToolAffectedArea;
import pixelitor.tools.UserDrag;
import pixelitor.utils.GUIUtils;
import pixelitor.utils.OKCancelDialog;

import javax.swing.*;
import java.awt.BasicStroke;
import java.awt.Cursor;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

/**
 * The Shapes Tool
 */
public class ShapesTool extends Tool {
    private final EnumComboBoxModel<ShapesAction> actionModel = new EnumComboBoxModel<>(ShapesAction.class);
    private final EnumComboBoxModel<ShapeType> typeModel = new EnumComboBoxModel<>(ShapeType.class);
    private final EnumComboBoxModel<TwoPointBasedPaint> fillModel = new EnumComboBoxModel<>(TwoPointBasedPaint.class);
    private final EnumComboBoxModel<TwoPointBasedPaint> strokeFillModel = new EnumComboBoxModel<>(TwoPointBasedPaint.class);

    private final RangeParam strokeWidthParam = new RangeParam("Stroke Width", 1, 100, 5);

    // controls in the Stroke Settings dialog
    private final ButtonModel dashedModel = new JToggleButton.ToggleButtonModel();
    private final EnumComboBoxModel<BasicStrokeCap> strokeCapModel = new EnumComboBoxModel<>(BasicStrokeCap.class);
    private final EnumComboBoxModel<BasicStrokeJoin> strokeJoinModel = new EnumComboBoxModel<>(BasicStrokeJoin.class);
    private final EnumComboBoxModel<StrokeType> strokeTypeModel = new EnumComboBoxModel<>(StrokeType.class);

    private JDialog strokeDialog;
    private JButton strokeSettingsButton;
    private BasicStroke basicStrokeForOpenShapes;
    private final JComboBox<TwoPointBasedPaint> strokeFillCombo;
    private final JComboBox<TwoPointBasedPaint> fillCombo = new JComboBox(fillModel);
    private JButton effectsButton;
    private OKCancelDialog effectsDialog;
    private EffectsPanel effectsPanel;

    private Shape backupSelectionShape = null;
    private boolean drawing = false;

    private Stroke stroke;


    public ShapesTool() {
        super('s', "Shapes", "shapes_tool_icon.gif",
                "Click and drag to draw a shape. Hold SPACE down while drawing to move the shape. ",
                Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR), true, true, false, ClipStrategy.IMAGE_ONLY);

        strokeFillModel.setSelectedItem(TwoPointBasedPaint.BACKGROUND);
        strokeFillCombo = new JComboBox(strokeFillModel);

        spaceDragBehavior = true;
    }

    @Override
    public void initSettingsPanel() {
        toolSettingsPanel.add(new JLabel("Shape:"));
        final JComboBox<ShapeType> shapeTypeCB = new JComboBox(typeModel);
        toolSettingsPanel.add(shapeTypeCB);

        // make sure all values are visible without a scrollbar
        shapeTypeCB.setMaximumRowCount(ShapeType.values().length);


        toolSettingsPanel.add(new JLabel("Action:"));
        JComboBox<ShapesAction> actionCB = new JComboBox(actionModel);
        toolSettingsPanel.add(actionCB);

        actionCB.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                enableSettings();
            }
        });

        toolSettingsPanel.add(new JLabel("Fill:"));
        toolSettingsPanel.add(fillCombo);

        toolSettingsPanel.add(new JLabel("Stroke:"));
        toolSettingsPanel.add(strokeFillCombo);

        strokeSettingsButton = new JButton("Stroke Settings...");
        toolSettingsPanel.add(strokeSettingsButton);
        strokeSettingsButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                initAndShowStrokeSettingsDialog();
            }
        });

        effectsButton = new JButton("Effects...");
        toolSettingsPanel.add(effectsButton);
        effectsButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showEffectsDialog();
            }
        });

        enableSettings();
    }

    private void showEffectsDialog() {
        if (effectsPanel == null) {
            effectsPanel = new EffectsPanel(null);
        }

        effectsDialog = new OKCancelDialog(effectsPanel, "Effects") {
            @Override
            protected void dialogAccepted() {
                super.dialogAccepted();
                closeDialog(effectsDialog);
                effectsPanel.updateEffectsFromGUI();
            }

            @Override
            protected void dialogCanceled() {
                super.dialogCanceled();
                closeDialog(effectsDialog);
            }
        };
        effectsDialog.setVisible(true);
    }

    @Override
    public void toolMousePressed(MouseEvent e, ImageComponent ic) {
        Composition comp = ic.getComp();
        Selection selection = comp.getSelection();
        if (selection != null) {
            backupSelectionShape = selection.getShape();
        } else {
            backupSelectionShape = null;
        }
    }

    @Override
    public void toolMouseDragged(MouseEvent e, ImageComponent ic) {
        drawing = true;
        userDrag.setStartFromCenter(e.isAltDown());

        Composition comp = ic.getComp();

        comp.imageChanged(true, false); // TODO optimize, the whole image should not be repainted
    }

    @Override
    public void toolMouseReleased(MouseEvent e, ImageComponent ic) {
        userDrag.setStartFromCenter(e.isAltDown());


        Composition comp = ic.getComp();

        ShapesAction action = actionModel.getSelectedItem();
        boolean selectionMode = action.createSelection();
        if (!selectionMode) {
//            saveImageForUndo(comp);

            int thickness = 0;
            int extraStrokeThickness = 0;
            if (action.enableStrokePaintSelection()) {
                thickness = strokeWidthParam.getValue();

                extraStrokeThickness = strokeTypeModel.getSelectedItem().getExtraWidth(thickness);
                thickness += extraStrokeThickness;
            }

            int effectThickness = 0;
            if (effectsPanel != null) {
                effectThickness = effectsPanel.getMaxEffectThickness();

                // the extra stroke thickness must be added to this because the effect can be on the stroke
                effectThickness += extraStrokeThickness;
            }

            if (effectThickness > thickness) {
                thickness = effectThickness;
            }

            ShapeType shapeType = typeModel.getSelectedItem();
            Shape currentShape = shapeType.getShape(userDrag);
            Rectangle shapeBounds = currentShape.getBounds();
            shapeBounds.grow(thickness, thickness);

            ToolAffectedArea affectedArea = new ToolAffectedArea(comp, shapeBounds, false);
            saveSubImageForUndo(comp.getActiveImageLayer().getBufferedImage(), affectedArea);
        }

        paintShapeOnIC(comp, userDrag);

        if (selectionMode) {
            Selection selection = comp.getSelection();
            if (selection != null) {
                selection.clipToCompSize(comp); // the selection can be too big

                PixelitorEdit edit;
                if (backupSelectionShape != null) {
                    edit = new SelectionChangeEdit(comp, backupSelectionShape, "Selection Change");
                } else {
                    edit = new NewSelectionEdit(comp, selection.getShape());
                }
                History.addEdit(edit);
            }
        }

        drawing = false;
        stroke = null;
        comp.imageChanged(true, true);
    }

    private void enableSettings() {
        ShapesAction action = actionModel.getSelectedItem();
        enableEffectSettings(action.drawEffects());
        enableStrokeSettings(action.enableStrokeSettings());
        enableFillPaintSelection(action.enableFillPaintSelection());
        enableStrokePaintSelection(action.enableStrokePaintSelection());
    }

    private void initAndShowStrokeSettingsDialog() {
        if (strokeDialog == null) {
            strokeDialog = new StrokeSettingsDialog(strokeWidthParam, strokeCapModel,
                    strokeJoinModel, strokeTypeModel, dashedModel);
        }

        GUIUtils.centerOnScreen(strokeDialog);
        strokeDialog.setVisible(true);
    }

    private static void closeDialog(JDialog d) {
        if (d != null) {
            d.setVisible(false);
            d.dispose();
        }
    }

    @Override
    protected void toolEnded() {
        super.toolEnded();
        closeDialog(strokeDialog);
        closeDialog(effectsDialog);
    }

    @Override
    public void paintOverLayer(Graphics2D g) {
        if (drawing) {
            paintShape(g, userDrag);
        }
    }

    /**
     * Paint a shape on the given ImageComponent. Can be used programmatically.
     * The start and end point points are given relative to the Composition (not Layer)
     */
    public void paintShapeOnIC(Composition comp, UserDrag userDrag) {
        ImageLayer layer = (ImageLayer) comp.getActiveLayer();
        int translationX = -layer.getTranslationX();
        int translationY = -layer.getTranslationY();

        BufferedImage bi = layer.getBufferedImage();
        Graphics2D g2 = bi.createGraphics();
        g2.translate(translationX, translationY);
        comp.setSelectionClipping(g2, null);

        paintShape(g2, userDrag);
        g2.dispose();
    }

    /**
     * Paints the selected shape on the given Graphics2D within the bounds of the given UserDrag
     * Called by paintOnImage while dragging, and by paintShapeOnIC on mouse release
     */
    private void paintShape(Graphics2D g, UserDrag userDrag) {
        if (userDrag.isClick()) {
            return;
        }

        if (basicStrokeForOpenShapes == null) {
            basicStrokeForOpenShapes = new BasicStroke(1);
        }

        ShapeType shapeType = typeModel.getSelectedItem();
        Shape currentShape = shapeType.getShape(userDrag);

        ShapesAction action = actionModel.getSelectedItem();

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);


        if (action.hasFill()) {
            TwoPointBasedPaint fillType = fillModel.getSelectedItem();
            if (shapeType.isClosed()) {
                g.setPaint(fillType.getPaint(userDrag));
                g.fill(currentShape);
            } else if (!action.hasStroke()) {
                // special case: a shape that is not closed can be only stroked, even if stroke is disabled
                // stroke it with the basic stroke
                g.setStroke(basicStrokeForOpenShapes);
                g.setPaint(fillType.getPaint(userDrag));
                g.draw(currentShape);
            }
        } else {
            // no fill
        }

        if (action.hasStroke()) {
            TwoPointBasedPaint strokeFill = strokeFillModel.getSelectedItem();
            if(stroke == null) {
                // During a single mouse drag, only one stroke should be created
                // This is particularly important for "random shape"
                stroke = createStroke();
            }
            g.setStroke(stroke);
            g.setPaint(strokeFill.getPaint(userDrag));
            g.draw(currentShape);
        } else {
            // no stroke
        }

        if (action.drawEffects()) {
            if (effectsPanel != null) {
                AreaEffect[] areaEffects = effectsPanel.getEffectsAsArray();
                for (AreaEffect effect : areaEffects) {
                    if (action.hasFill()) {
                        effect.apply(g, currentShape, 0, 0);
                    } else if (action.hasStroke()) { // special case if there is only stroke
                        if (stroke == null) {
                            stroke = createStroke();
                        }
                        effect.apply(g, stroke.createStrokedShape(currentShape), 0, 0);
                    } else { // "effects only"
                        effect.apply(g, currentShape, 0, 0);
                    }
                }
            }
        }


        if (action.createSelection()) {
            Shape selectionShape = null;
            if (action.enableStrokeSettings()) {
                if (stroke == null) {
                    stroke = createStroke();
                }
                selectionShape = stroke.createStrokedShape(currentShape);
            } else if (!shapeType.isClosed()) {
                if (basicStrokeForOpenShapes == null) {
                    throw new IllegalStateException();
                }
                selectionShape = basicStrokeForOpenShapes.createStrokedShape(currentShape);
            } else {
                selectionShape = currentShape;
            }

            Composition comp = ImageComponents.getActiveComp(); // TODO there should be a more direct way to get the reference
            Selection selection = comp.getSelection();
            if (selection == null) {
                comp.createSelectionFromShape(selectionShape);
            } else {
                selection.setShape(selectionShape);
            }
        }
    }

    private Stroke createStroke() {
        int strokeWidth = strokeWidthParam.getValue();

        float[] dashFloats = null;
        if (dashedModel.isSelected()) {
            dashFloats = new float[]{2 * strokeWidth, 2 * strokeWidth};
        }

        Stroke stroke = strokeTypeModel.getSelectedItem().getStroke(
                strokeWidth,
                strokeCapModel.getSelectedItem().getValue(),
                strokeJoinModel.getSelectedItem().getValue(),
                dashFloats
        );

        return stroke;
    }

    /**
     * Used for testing
     */
    public void setShapeType(ShapeType newType) {
        typeModel.setSelectedItem(newType);
    }

    public boolean isDrawing() {
        return drawing;
    }

    private void enableStrokeSettings(boolean b) {
        strokeSettingsButton.setEnabled(b);

        if (!b) {
            closeDialog(strokeDialog);
        }
    }

    private void enableEffectSettings(boolean b) {
        effectsButton.setEnabled(b);

        if (!b) {
            closeDialog(effectsDialog);
        }
    }

    private void enableStrokePaintSelection(boolean b) {
        strokeFillCombo.setEnabled(b);
    }

    private void enableFillPaintSelection(boolean b) {
        fillCombo.setEnabled(b);
    }

    /**
     * Can be used for debugging
     */
    public void setAction(ShapesAction action) {
        actionModel.setSelectedItem(action);
    }

    /**
     * Can be used for debugging
     */
    public void setStrokeType(StrokeType newStrokeType) {
        strokeTypeModel.setSelectedItem(newStrokeType);
    }
}
