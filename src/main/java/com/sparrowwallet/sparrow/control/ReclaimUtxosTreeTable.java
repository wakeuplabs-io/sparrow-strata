package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.wallet.TableType;
import com.sparrowwallet.sparrow.strata.reclaim.ReclaimEntry;
import com.sparrowwallet.sparrow.strata.reclaim.ReclaimUtxosEntry;
import com.sparrowwallet.sparrow.wallet.Entry;
import com.sparrowwallet.sparrow.wallet.HashIndexEntry;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.Pos;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeTableCell;
import javafx.scene.control.TreeTableColumn;
import javafx.util.Duration;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

public class ReclaimUtxosTreeTable extends CoinTreeTable {
    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public void initialize(List<ReclaimEntry> entries) {
        getColumns().clear();
        getStyleClass().add("utxos-treetable");
        if(!entries.isEmpty()) {
            setUnitFormat(entries.get(0).getWallet());
        }

        TreeTableColumn<Entry, Entry> dateCol = new TreeTableColumn<>("Date");
        dateCol.setCellValueFactory(param -> new ReadOnlyObjectWrapper<>(param.getValue().getValue()));
        dateCol.setCellFactory(column -> new ReclaimDateCell());
        dateCol.setSortable(true);
        dateCol.setComparator((o1, o2) -> compareDates(o1, o2));
        getColumns().add(dateCol);

        TreeTableColumn<Entry, Entry> outputCol = new TreeTableColumn<>("Output");
        outputCol.setCellValueFactory(param -> new ReadOnlyObjectWrapper<>(param.getValue().getValue()));
        outputCol.setCellFactory(column -> new EntryCell());
        outputCol.setSortable(true);
        outputCol.setComparator((o1, o2) -> {
            ReclaimEntry entry1 = (ReclaimEntry)o1;
            ReclaimEntry entry2 = (ReclaimEntry)o2;
            int hashCompare = entry1.getHashIndex().getHash().toString().compareTo(entry2.getHashIndex().getHash().toString());
            if(hashCompare != 0) {
                return hashCompare;
            }
            return (int)(entry1.getHashIndex().getIndex() - entry2.getHashIndex().getIndex());
        });
        getColumns().add(outputCol);

        TreeTableColumn<Entry, Address> addressCol = new TreeTableColumn<>("Address");
        addressCol.setCellValueFactory(param -> {
            Entry entry = param.getValue().getValue();
            if(entry instanceof ReclaimEntry reclaimEntry) {
                return new ReadOnlyObjectWrapper<>(reclaimEntry.getAddress());
            }
            return new ReadOnlyObjectWrapper<>(null);
        });
        addressCol.setCellFactory(column -> new ReclaimAddressCell());
        addressCol.setSortable(true);
        addressCol.setComparator(Comparator.comparing(address -> address == null ? "" : address.toString()));
        getColumns().add(addressCol);

        TreeTableColumn<Entry, Number> amountCol = new TreeTableColumn<>("Value");
        amountCol.setCellValueFactory(param -> new ReadOnlyObjectWrapper<>(param.getValue().getValue().getValue()));
        amountCol.setCellFactory(column -> new CoinCell());
        amountCol.setSortable(true);
        getColumns().add(amountCol);
        setTreeColumn(amountCol);

        setShowRoot(false);
        setEditable(false);
        setTableType(TableType.UTXOS);
        setupColumnWidths();
        updateEntries(entries);
        setupColumnSort(getColumns().size() - 1, TreeTableColumn.SortType.DESCENDING);
        getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
    }

    public void updateEntries(List<ReclaimEntry> entries) {
        if(!entries.isEmpty()) {
            setUnitFormat(entries.get(0).getWallet());
        }

        ReclaimUtxosEntry rootEntry = new ReclaimUtxosEntry(entries);
        RecursiveTreeItem<Entry> rootItem = new RecursiveTreeItem<>(rootEntry, Entry::getChildren);
        setRoot(rootItem);
        rootItem.setExpanded(true);
        resetSortColumn();
    }

    private static int compareDates(Entry o1, Entry o2) {
        Date d1 = o1 instanceof HashIndexEntry e1 ? e1.getHashIndex().getDate() : null;
        Date d2 = o2 instanceof HashIndexEntry e2 ? e2.getHashIndex().getDate() : null;
        if(d1 == null && d2 == null) {
            return 0;
        }
        if(d1 == null) {
            return 1;
        }
        if(d2 == null) {
            return -1;
        }
        return d2.compareTo(d1);
    }

    private static class ReclaimDateCell extends TreeTableCell<Entry, Entry> {
        public ReclaimDateCell() {
            setAlignment(Pos.CENTER_LEFT);
            getStyleClass().add("date-cell");
        }

        @Override
        protected void updateItem(Entry entry, boolean empty) {
            super.updateItem(entry, empty);
            EntryCell.applyRowStyles(this, entry);

            if(empty || !(entry instanceof HashIndexEntry hashIndexEntry)) {
                setText(null);
                setGraphic(null);
                setTooltip(null);
                return;
            }

            if(hashIndexEntry.getHashIndex().getDate() != null) {
                setText(DATE_FORMAT.format(hashIndexEntry.getHashIndex().getDate()));
            } else {
                setText("Unknown");
            }

            Tooltip tooltip = new Tooltip();
            tooltip.setShowDelay(Duration.millis(250));
            tooltip.setText(Integer.toString(hashIndexEntry.getHashIndex().getHeight()));
            setTooltip(tooltip);
            setGraphic(null);
        }
    }

    private static class ReclaimAddressCell extends TreeTableCell<Entry, Address> {
        public ReclaimAddressCell() {
            setAlignment(Pos.CENTER_LEFT);
            setContentDisplay(ContentDisplay.RIGHT);
            getStyleClass().add("address-cell");
        }

        @Override
        protected void updateItem(Address address, boolean empty) {
            super.updateItem(address, empty);
            Entry entry = empty ? null : getTableRow().getTreeItem().getValue();
            EntryCell.applyRowStyles(this, entry);

            if(empty || address == null) {
                setText(null);
                setGraphic(null);
                setTooltip(null);
                return;
            }

            setText(address.toString());
            Tooltip tooltip = new Tooltip();
            tooltip.setShowDelay(Duration.millis(250));
            tooltip.setText(address.toString());
            setTooltip(tooltip);
            setGraphic(null);
        }
    }
}
