package dev.andre.modelanchor;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Desktop-side editor, opened by the O key. The game can keep rendering while it is open. */
public final class ModelAnchorEditor {
    private static JFrame frame;
    private static DefaultListModel<Attachment> listModel;
    private static JList<Attachment> attachmentList;
    private static JComboBox<String> bodyPart;
    private static JCheckBox enabled;
    private static JTextField scale, x, y, z, rx, ry, rz;
    private static JLabel status;

    private ModelAnchorEditor() {
    }

    public static void open() {
        SwingUtilities.invokeLater(() -> {
            if (frame != null && frame.isDisplayable()) {
                frame.setVisible(true);
                frame.toFront();
                refreshList(-1);
                return;
            }
            createWindow();
        });
    }

    private static void createWindow() {
        frame = new JFrame("ModelAnchor — Player Attachments");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setMinimumSize(new Dimension(650, 475));
        frame.setSize(750, 540);
        frame.setLocationByPlatform(true);

        listModel = new DefaultListModel<>();
        attachmentList = new JList<>(listModel);
        attachmentList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        attachmentList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) loadSelectedFields();
        });

        JPanel left = new JPanel(new BorderLayout(6, 6));
        left.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 4));
        left.add(new JLabel("Attachments"), BorderLayout.NORTH);
        left.add(new JScrollPane(attachmentList), BorderLayout.CENTER);
        JPanel listButtons = new JPanel(new GridLayout(0, 1, 5, 5));
        JButton importButton = new JButton("Import OBJ…");
        importButton.addActionListener(event -> importObj());
        JButton removeButton = new JButton("Remove selected");
        removeButton.addActionListener(event -> removeSelected());
        listButtons.add(importButton);
        listButtons.add(removeButton);
        left.add(listButtons, BorderLayout.SOUTH);

        JPanel editor = new JPanel(new GridLayout(0, 2, 8, 8));
        editor.setBorder(BorderFactory.createEmptyBorder(14, 10, 10, 14));
        bodyPart = new JComboBox<>(new String[]{"HEAD", "BODY", "RIGHT_ARM", "LEFT_ARM", "RIGHT_LEG", "LEFT_LEG"});
        enabled = new JCheckBox("Enabled", true);
        scale = field("0.25"); x = field("0"); y = field("0"); z = field("0");
        rx = field("0"); ry = field("0"); rz = field("0");
        addRow(editor, "Attach to", bodyPart);
        addRow(editor, "Visibility", enabled);
        addRow(editor, "Scale", scale);
        addRow(editor, "Offset X (model pixels)", x);
        addRow(editor, "Offset Y (model pixels)", y);
        addRow(editor, "Offset Z (model pixels)", z);
        addRow(editor, "Rotation X (degrees)", rx);
        addRow(editor, "Rotation Y (degrees)", ry);
        addRow(editor, "Rotation Z (degrees)", rz);

        JButton apply = new JButton("Apply / Save");
        apply.addActionListener(event -> applySelected());
        JButton reload = new JButton("Reload OBJ meshes");
        reload.addActionListener(event -> {
            try {
                applySelected();
                ObjMeshCache.preload(AttachmentStore.snapshot());
                showStatus("Saved and reloaded model meshes.");
            } catch (RuntimeException exception) {
                showError(exception.getMessage());
            }
        });
        JPanel bottomButtons = new JPanel(new GridLayout(1, 2, 6, 6));
        bottomButtons.add(apply);
        bottomButtons.add(reload);
        addRow(editor, "", bottomButtons);

        status = new JLabel("Press F5 in Minecraft to view attachments in third person.");
        status.setBorder(BorderFactory.createEmptyBorder(0, 12, 10, 12));
        frame.add(left, BorderLayout.WEST);
        frame.add(editor, BorderLayout.CENTER);
        frame.add(status, BorderLayout.SOUTH);
        refreshList(AttachmentStore.snapshot().isEmpty() ? -1 : 0);
        frame.setVisible(true);
    }

    private static JTextField field(String value) {
        return new JTextField(value, 12);
    }

    private static void addRow(JPanel panel, String label, java.awt.Component component) {
        panel.add(new JLabel(label));
        panel.add(component);
    }

    private static void importObj() {
        JFileChooser chooser = new JFileChooser(AttachmentStore.modelsDirectory().toFile());
        chooser.setDialogTitle("Choose a Wavefront OBJ model");
        chooser.setFileFilter(new FileNameExtensionFilter("Wavefront OBJ (*.obj)", "obj"));
        if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) return;
        try {
            Path imported = AttachmentStore.importObj(chooser.getSelectedFile().toPath());
            Attachment attachment = new Attachment();
            attachment.path = imported.toString();
            attachment.name = imported.getFileName().toString();
            attachment.part = "HEAD";
            attachment.scale = 0.25;
            List<Attachment> all = AttachmentStore.snapshot();
            all.add(attachment);
            AttachmentStore.replaceAll(all);
            refreshList(all.size() - 1);
            showStatus("Imported " + attachment.name + " — use Apply / Save after adjusting it.");
        } catch (Exception exception) {
            showError("Import failed: " + exception.getMessage());
        }
    }

    private static void removeSelected() {
        int selected = attachmentList.getSelectedIndex();
        if (selected < 0) return;
        List<Attachment> all = AttachmentStore.snapshot();
        if (selected < all.size()) all.remove(selected);
        AttachmentStore.replaceAll(all);
        refreshList(all.isEmpty() ? -1 : Math.min(selected, all.size() - 1));
        showStatus("Removed attachment.");
    }

    private static void applySelected() {
        int selected = attachmentList.getSelectedIndex();
        if (selected < 0) {
            showStatus("Import an OBJ first.");
            return;
        }
        try {
            List<Attachment> all = AttachmentStore.snapshot();
            Attachment attachment = all.get(selected);
            attachment.part = (String) bodyPart.getSelectedItem();
            attachment.enabled = enabled.isSelected();
            attachment.scale = number(scale, "scale");
            if (attachment.scale <= 0.0 || attachment.scale > 100.0) throw new IllegalArgumentException("Scale must be greater than 0 and no more than 100.");
            attachment.x = number(x, "X offset");
            attachment.y = number(y, "Y offset");
            attachment.z = number(z, "Z offset");
            attachment.rotateX = number(rx, "X rotation");
            attachment.rotateY = number(ry, "Y rotation");
            attachment.rotateZ = number(rz, "Z rotation");
            AttachmentStore.replaceAll(all);
            refreshList(selected);
            showStatus("Saved. The model is attached; press F5 to check it in third person.");
        } catch (RuntimeException exception) {
            showError(exception.getMessage() == null ? "One of the values is invalid." : exception.getMessage());
        }
    }

    private static double number(JTextField field, String label) {
        try {
            double value = Double.parseDouble(field.getText().trim());
            if (!Double.isFinite(value)) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(label + " must be a valid number.");
        }
    }

    private static void loadSelectedFields() {
        if (attachmentList == null) return;
        int selected = attachmentList.getSelectedIndex();
        List<Attachment> all = AttachmentStore.snapshot();
        if (selected < 0 || selected >= all.size()) return;
        Attachment attachment = all.get(selected);
        bodyPart.setSelectedItem(attachment.part);
        enabled.setSelected(attachment.enabled);
        scale.setText(Double.toString(attachment.scale));
        x.setText(Double.toString(attachment.x)); y.setText(Double.toString(attachment.y)); z.setText(Double.toString(attachment.z));
        rx.setText(Double.toString(attachment.rotateX)); ry.setText(Double.toString(attachment.rotateY)); rz.setText(Double.toString(attachment.rotateZ));
    }

    private static void refreshList(int selectedIndex) {
        if (listModel == null || attachmentList == null) return;
        List<Attachment> attachments = AttachmentStore.snapshot();
        listModel.clear();
        for (Attachment attachment : attachments) listModel.addElement(attachment.copy());
        if (!attachments.isEmpty() && selectedIndex >= 0) attachmentList.setSelectedIndex(Math.min(selectedIndex, attachments.size() - 1));
        else attachmentList.clearSelection();
    }

    private static void showStatus(String message) {
        if (status != null) status.setText(message);
    }

    private static void showError(String message) {
        JOptionPane.showMessageDialog(frame, message, "ModelAnchor", JOptionPane.ERROR_MESSAGE);
    }
}
