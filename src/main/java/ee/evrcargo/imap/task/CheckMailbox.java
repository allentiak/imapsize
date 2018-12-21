package ee.evrcargo.imap.task;

import ee.evrcargo.imap.Configuration;
import ee.evrcargo.imap.tree.FolderPath;
import ee.evrcargo.imap.tree.FolderState;
import ee.evrcargo.imap.tree.ImapTree;

import javax.mail.*;
import java.text.NumberFormat;
import java.util.*;

public class CheckMailbox implements Task {
    private static NumberFormat nf = NumberFormat.getNumberInstance();
    private final Properties conf;
    private final int maxRetries;

    public CheckMailbox(String config) {
        conf = Configuration.getInstance().getProps(config);
        maxRetries = Integer.parseInt(conf.getProperty("mailbox.retry.count"));
    }

    @Override
    public void execute() {
        System.out.println("Checking mailbox " + conf.getProperty("mailbox.user") + " at " + conf.getProperty("mailbox.domain"));
        ImapTree tree = new ImapTree(conf);
        List<FolderPath> paths = tree.build();

        // Crawl the folders
        System.out.println("\n                  Checking size of the folders...");
        int retry = 0;
        int pathIdx = 0;
        FolderState folderState = new FolderState();
        List<FolderState> allStates = new ArrayList<>();
        while (retry < maxRetries && pathIdx < paths.size()) {
            if (getFolderInfo(paths.get(pathIdx), folderState)) {
                allStates.add(folderState);
                pathIdx++;
                folderState = new FolderState();
            } else {
                retry++;
                System.out.println("Failed message folder: " + paths.get(pathIdx).getPath() + " number:" + folderState.getCurrentImap());
                System.out.println("Retry " + retry + "/" + maxRetries);
            }
        }

        // Print totals
        int messageTotal = 0;
        long sizeTotal = 0L;
        for (FolderState state : allStates) {
            messageTotal += state.getCurrentImap();
            sizeTotal += state.getSize();
        }

        System.out.println("\nNB! Filtered folders are: " + conf.getProperty("mailbox.ignored.folders"));
        System.out.println("Total messages: " + messageTotal);
        System.out.println("Mailbox size: " + nf.format(sizeTotal) + " bytes");
    }

    private boolean getFolderInfo(FolderPath path, FolderState state) {
        Session session = Session.getInstance(conf, null);
        Store store = null;
        try {
            store = session.getStore();
            store.connect(conf.getProperty("mailbox.user"), conf.getProperty("mailbox.password"));
            Folder folder = store.getFolder(path.getPath());
            if (!folder.isOpen()) {
                folder.open(Folder.READ_ONLY);
            }
            System.out.println();
            String tab = "  ";
            System.out.println(String.join("", Collections.nCopies(path.getDepth(), tab)) + "Full Name: " + folder.getFullName());
            System.out.println(String.join("", Collections.nCopies(path.getDepth(), tab)) + "URL:       " + folder.getURLName());
            System.out.println(String.join("", Collections.nCopies(path.getDepth(), tab)) + "Messages:  " + folder.getMessageCount());

            Message[] messages = folder.getMessages();
            int totalMessages = folder.getMessageCount();
            for (int i = state.getCurrentImap(); i < totalMessages; i++) {
                System.out.print("Reading message " + (i + 1) + "/" + totalMessages + "\r");
                state.setSize(state.getSize() + messages[i].getSize());
                state.setCurrentImap(i + 1);
            }

            System.out.println(String.join("", Collections.nCopies(path.getDepth(), tab)) + "Size:  " + nf.format(state.getSize()) + " bytes");
            store.close();
            return true;
        } catch (MessagingException e) {
            e.printStackTrace();
            return false;
        } finally {
            try { if (store != null) store.close(); } catch (MessagingException e) { e.printStackTrace(); }
        }
    }
}
