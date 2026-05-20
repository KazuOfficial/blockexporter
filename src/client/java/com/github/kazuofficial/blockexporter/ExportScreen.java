package com.github.kazuofficial.blockexporter;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.CommonColors;
import net.minecraft.util.Util;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ExportScreen extends Screen {
	private static final int PROGRESS_BAR_WIDTH = 280;
	private static final int PROGRESS_BAR_HEIGHT = 24;
	private static final int ITEM_SIZE = 48;
	private static final int PANEL_PADDING = 20;

	private static final int PROGRESS_BACKGROUND = 0xFF2D2D30;
	private static final int PROGRESS_FILL = 0xFF0E7B0E;
	private static final int PROGRESS_BORDER = 0xFF555555;
	private static final int ITEM_FRAME_COLOR = 0xFF8B8B8B;
	private static final int ACCENT_COLOR = 0xFF4A90E2;

	private static final int BATCH_SIZE = 32;

	private final List<Item> allItems;
	private List<Item> itemsToExport;
	private boolean finishedWithErrors = false;
	private int failedItemCount = 0;
	private int currentItemIndex = 0;
	private final AtomicInteger completedItems = new AtomicInteger(0);
	private boolean isExporting = false;
	private Button startCancelButton;
	private Button doneButton;
	private ItemRenderer itemRenderer;
	private int exportSize = 64;
	private Path exportDirectory;
	private AbstractSliderButton sizeSlider;
	private static final List<Integer> EXPORT_SIZES = Arrays.asList(16, 32, 64, 128, 256, 512, 1024);

	public ExportScreen() {
		super(Minecraft.getInstance(), Minecraft.getInstance().font, Component.translatable("screen.blockexporter.title"));
		this.allItems = new ArrayList<>(BuiltInRegistries.ITEM.stream().filter(item -> item != Items.AIR).toList());
		this.itemsToExport = new ArrayList<>(this.allItems);
	}

	@Override
	protected void init() {
		super.init();
		this.exportDirectory = this.minecraft.gameDirectory.toPath().resolve("item_exports");

		int buttonWidth = 100;
		int buttonHeight = 24;
		int spacing = 12;
		int totalWidth = buttonWidth * 3 + spacing * 2;
		int buttonsX = (this.width - totalWidth) / 2;

		int sliderWidth = 240;
		this.sizeSlider = new AbstractSliderButton(this.width / 2 - sliderWidth / 2, this.height - 75, sliderWidth, 20, Component.empty(), 0) {
			{
				int initialSizeIndex = EXPORT_SIZES.indexOf(exportSize);
				if (initialSizeIndex == -1) {
					initialSizeIndex = 2;
				}
				this.value = initialSizeIndex / (double) (EXPORT_SIZES.size() - 1);
				this.applyValue();
				this.updateMessage();
			}

			@Override
			protected void updateMessage() {
				int size = EXPORT_SIZES.get((int) Math.round(this.value * (EXPORT_SIZES.size() - 1)));
				setMessage(Component.literal("Export Size: " + size + "×" + size + " pixels"));
			}

			@Override
			protected void applyValue() {
				int sizeIndex = (int) Math.round(this.value * (EXPORT_SIZES.size() - 1));
				exportSize = EXPORT_SIZES.get(sizeIndex);
			}
		};
		this.addRenderableWidget(this.sizeSlider);

		this.startCancelButton = this.addRenderableWidget(Button.builder(
			Component.literal(isExporting ? "Cancel Export" : "▶ Start Export"),
			button -> {
				if (isExporting) {
					isExporting = false;
					this.updateButtonStates();
					BlockExporter.LOGGER.info("Export cancelled by user.");
				} else {
					isExporting = true;
					this.finishedWithErrors = false;
					this.failedItemCount = 0;
					this.itemsToExport = new ArrayList<>(this.allItems);

					if (this.itemRenderer != null) {
						this.itemRenderer.close();
					}
					this.itemRenderer = new ItemRenderer(this.exportSize);
					this.currentItemIndex = 0;
					this.completedItems.set(0);
					BlockExporter.LOGGER.info("Starting fast batch export of {} items with batch size {}",
						itemsToExport.size(), BATCH_SIZE);
					this.updateButtonStates();
				}
			})
			.bounds(buttonsX, this.height - 40, buttonWidth, buttonHeight)
			.build()
		);

		this.addRenderableWidget(Button.builder(
			Component.literal("📁 Open Folder"),
			button -> {
				try {
					Files.createDirectories(this.exportDirectory);
					Util.getPlatform().openFile(this.exportDirectory.toFile());
				} catch (IOException e) {
					BlockExporter.LOGGER.error("Couldn't open export folder", e);
				}
			})
			.bounds(buttonsX + buttonWidth + spacing, this.height - 40, buttonWidth, buttonHeight)
			.build()
		);

		this.doneButton = this.addRenderableWidget(Button.builder(
			CommonComponents.GUI_DONE,
			button -> this.onClose())
			.bounds(buttonsX + (buttonWidth + spacing) * 2, this.height - 40, buttonWidth, buttonHeight)
			.build()
		);
		updateButtonStates();
	}

	private void updateButtonStates() {
		if (isExporting) {
			startCancelButton.setMessage(Component.literal("Cancel Export"));
			sizeSlider.active = false;
			doneButton.active = false;
		} else {
			startCancelButton.setMessage(Component.literal("▶ Start Export"));
			sizeSlider.active = true;
			doneButton.active = true;
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int panelHeight = 240;
		int panelY = (this.height - panelHeight) / 2 - 20;

		graphics.centeredText(this.font, this.title, this.width / 2, panelY + PANEL_PADDING, ACCENT_COLOR);

		int itemSectionY = panelY + PANEL_PADDING + 25;
		int displayIndex = Math.min(currentItemIndex, itemsToExport.size() - 1);
		if (displayIndex >= 0 && displayIndex < itemsToExport.size()) {
			ItemStack stack = new ItemStack(itemsToExport.get(displayIndex));

			int itemFrameSize = ITEM_SIZE + 8;
			int itemFrameX = (this.width - itemFrameSize) / 2;
			int itemFrameY = itemSectionY;

			graphics.fill(itemFrameX - 1, itemFrameY - 1, itemFrameX + itemFrameSize + 1, itemFrameY + itemFrameSize + 1, ITEM_FRAME_COLOR);
			graphics.fill(itemFrameX, itemFrameY, itemFrameX + itemFrameSize, itemFrameY + itemFrameSize, 0xFF1E1E1E);

			float scale = ITEM_SIZE / 16.0f;
			graphics.pose().pushMatrix();
			graphics.pose().translate(itemFrameX + 4 + ITEM_SIZE / 2.0f, itemFrameY + 4 + ITEM_SIZE / 2.0f);
			graphics.pose().scale(scale, scale);
			graphics.item(stack, -8, -8);
			graphics.pose().popMatrix();

			String itemName = stack.getHoverName().getString();
			if (itemName.length() > 25) {
				itemName = itemName.substring(0, 22) + "...";
			}
			graphics.centeredText(this.font, itemName, this.width / 2, itemSectionY + itemFrameSize + 8, CommonColors.WHITE);
		}

		int progressSectionY = itemSectionY + 85;

		int progressX = (this.width - PROGRESS_BAR_WIDTH) / 2;
		int progressY = progressSectionY;

		graphics.fill(progressX - 1, progressY - 1, progressX + PROGRESS_BAR_WIDTH + 1, progressY + PROGRESS_BAR_HEIGHT + 1, PROGRESS_BORDER);
		graphics.fill(progressX, progressY, progressX + PROGRESS_BAR_WIDTH, progressY + PROGRESS_BAR_HEIGHT, PROGRESS_BACKGROUND);

		boolean isComplete = currentItemIndex >= itemsToExport.size() && !isExporting;

		int completed = isComplete ? itemsToExport.size() - failedItemCount : completedItems.get();
		float progress = itemsToExport.isEmpty() ? 0 : (float) completed / itemsToExport.size();
		int progressWidth = (int) (PROGRESS_BAR_WIDTH * progress);
		if (progressWidth > 0) {
			graphics.fill(progressX, progressY, progressX + progressWidth, progressY + PROGRESS_BAR_HEIGHT, PROGRESS_FILL);
			graphics.fill(progressX, progressY, progressX + progressWidth, progressY + 2, 0xFF4CAF50);
		}

		String progressText = String.format("%d / %d items (%.1f%%)",
			completed, itemsToExport.size(), progress * 100);
		graphics.centeredText(this.font, progressText, this.width / 2, progressY + PROGRESS_BAR_HEIGHT + 8, CommonColors.WHITE);

		String statusText = isExporting ? "⚡ Exporting..." : (isComplete ? (finishedWithErrors ? "Finished with errors" : "Export Complete!") : "Ready to export");
		int statusColor = isExporting ? 0xFFFFAA00 : (isComplete ? (finishedWithErrors ? 0xFFFF5555 : 0xFF00FF00) : CommonColors.LIGHT_GRAY);
		graphics.centeredText(this.font, Component.literal(statusText), this.width / 2, progressY + PROGRESS_BAR_HEIGHT + 25, statusColor);

		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	@Override
	public void tick() {
		super.tick();
		if (isExporting) {
			if (currentItemIndex < itemsToExport.size()) {
				List<ItemStack> batch = new ArrayList<>();
				int batchEnd = Math.min(currentItemIndex + BATCH_SIZE, itemsToExport.size());

				for (int i = currentItemIndex; i < batchEnd; i++) {
					batch.add(new ItemStack(itemsToExport.get(i)));
				}

				if (itemRenderer != null && !batch.isEmpty()) {
					itemRenderer.exportItemsBatch(batch, completedItems);
				}

				currentItemIndex = batchEnd;

			} else if (completedItems.get() >= itemsToExport.size()) {
				isExporting = false;
				if (itemRenderer != null) {
					List<ItemStack> currentFailedItems = itemRenderer.getFailedExports();
					if (!currentFailedItems.isEmpty()) {
						this.finishedWithErrors = true;
						this.failedItemCount = currentFailedItems.size();
					}
				}
				updateButtonStates();

				if (!this.finishedWithErrors) {
					BlockExporter.LOGGER.info("Fast batch export completed! Exported {} items", itemsToExport.size());
				} else {
					BlockExporter.LOGGER.warn("Export finished with {} failures.", this.failedItemCount);
				}
			}
		}
	}

	@Override
	public void onClose() {
		if (this.itemRenderer != null) {
			this.itemRenderer.close();
		}
		super.onClose();
	}

	@Override
	public boolean isPauseScreen() {
		return true;
	}
}
