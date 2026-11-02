package dev.smallwright.controller.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import kotlin.Unit;
import org.junit.Test;

public final class SidebarTest {
    private static Picker picker(int selected, String... items) {
        Picker picker = new Picker();
        picker.setItems(List.of(items));
        picker.setSelection(selected);
        return picker;
    }

    @Test
    public void theCurrentProjectOpensInPlaceWithItsChatsThenNewChat() {
        Picker projects = picker(1, "Counter", "Notes", "Timer");
        Picker chats = picker(1, "App ideas", "Bigger buttons");

        assertEquals(
                List.of(
                        new TreeLine.Project(0, "Counter", false),
                        new TreeLine.Project(1, "Notes", true),
                        new TreeLine.Chat(0, "App ideas", false),
                        new TreeLine.Chat(1, "Bigger buttons", true),
                        TreeLine.NewChat.INSTANCE,
                        new TreeLine.Project(2, "Timer", false)),
                SidebarKt.projectTree(projects, chats, false));
    }

    @Test
    public void aProjectBeingOpenedShowsNoChatsYet() {
        Picker projects = picker(0, "Counter", "Notes");
        Picker chatsOfTheProjectLeft = picker(0, "Notes chat");

        assertEquals(
                List.of(
                        new TreeLine.Project(0, "Counter", true),
                        new TreeLine.Project(1, "Notes", false)),
                SidebarKt.projectTree(projects, chatsOfTheProjectLeft, true));
    }

    @Test
    public void withNoProjectChosenEveryProjectStaysClosed() {
        Picker projects = picker(-1, "Counter");
        Picker chats = picker(0, "App ideas");

        assertEquals(
                List.of(new TreeLine.Project(0, "Counter", false)),
                SidebarKt.projectTree(projects, chats, false));
    }

    @Test
    public void aDoubleTapOnNewChatMakesOneChatAndClosesTheMenu() {
        UiState ui = new UiState();
        ui.setMenuOpen(true);
        ui.getNewChat().setEnabled(true);
        int[] made = {0};
        ui.getNewChat().setOnClickListener(() -> made[0]++);

        SidebarKt.runFromMenu(ui, ui.getNewChat());
        SidebarKt.runFromMenu(ui, ui.getNewChat());

        assertEquals(1, made[0]);
        assertFalse(ui.getMenuOpen());
    }

    @Test
    public void aSecondTapWhileTheMenuSlidesShutPicksNothing() {
        UiState ui = new UiState();
        ui.setMenuOpen(true);
        ui.getChatPicker().setItems(List.of("App ideas", "Bigger buttons", "Colours"));
        ui.getProjectPicker().setItems(List.of("Counter", "Notes"));
        ui.getNewChat().setEnabled(true);
        List<String> picked = new ArrayList<>();
        ui.getChatPicker()
                .setOnSelect(
                        position -> {
                            picked.add("chat " + position);
                            return Unit.INSTANCE;
                        });
        ui.getProjectPicker()
                .setOnSelect(
                        position -> {
                            picked.add("project " + position);
                            return Unit.INSTANCE;
                        });
        ui.getNewChat().setOnClickListener(() -> picked.add("new chat"));

        assertTrue(SidebarKt.pickFromMenu(ui, ui.getChatPicker(), 1));
        assertTrue(SidebarKt.startClosing(ui));
        assertFalse(SidebarKt.pickFromMenu(ui, ui.getChatPicker(), 2));
        assertFalse(SidebarKt.pickFromMenu(ui, ui.getProjectPicker(), 1));
        SidebarKt.runFromMenu(ui, ui.getNewChat());
        assertFalse(SidebarKt.startClosing(ui));

        assertEquals(List.of("chat 1"), picked);
        assertEquals(1, ui.getChatPicker().getSelectedItemPosition());
    }

    @Test
    public void aProjectTapThatTheBuilderAlreadyTookDisablesTheNextOne() {
        UiState ui = new UiState();
        ui.setMenuOpen(true);
        ui.getProjectPicker().setItems(List.of("Counter", "Notes", "Timer"));
        List<Integer> picked = new ArrayList<>();
        ui.getProjectPicker()
                .setOnSelect(
                        position -> {
                            picked.add(position);
                            ui.getProjectPicker().setEnabled(false);
                            return Unit.INSTANCE;
                        });

        assertTrue(SidebarKt.pickFromMenu(ui, ui.getProjectPicker(), 1));
        assertFalse(SidebarKt.pickFromMenu(ui, ui.getProjectPicker(), 2));

        assertEquals(List.of(1), picked);
    }

    @Test
    public void installStaysOnProjectToolsBecauseAnInstalledUntestedAppShowsItWorksInstead() {
        UiState ui = new UiState();
        ui.getInstall().setEnabled(true);
        ui.getAcceptTest().setEnabled(true);

        assertSame(ui.getAcceptTest(), ChatKt.primaryAction(ui));
        assertTrue(ControllerScreenKt.projectTools(ui).contains(ui.getInstall()));
    }

    @Test
    public void afterAnUndoWithNoBuildOfThoseFilesBuildingIsTheNextStep() {
        UiState ui = new UiState();
        ui.getBuild().setEnabled(true);
        assertNull(ChatKt.primaryAction(ui));

        ui.getBuild().setText(Labels.BUILD_APP);

        assertSame(ui.getBuild(), ChatKt.primaryAction(ui));
        assertTrue(ControllerScreenKt.projectTools(ui).contains(ui.getUndo()));
    }
}
