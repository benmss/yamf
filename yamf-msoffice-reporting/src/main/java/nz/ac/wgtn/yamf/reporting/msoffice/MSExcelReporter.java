package nz.ac.wgtn.yamf.reporting.msoffice;

import com.google.common.collect.Lists;
import com.google.common.io.Files;
import nz.ac.wgtn.yamf.Attachment;
import nz.ac.wgtn.yamf.MarkingResultRecord;
import nz.ac.wgtn.yamf.reporting.Reporter;
import nz.ac.wgtn.yamf.reporting.StackTraceFilters;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.*;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Reporter producing MS Excel files that are editable.
 * @author jens dietrich
 */
public class MSExcelReporter implements Reporter {
    private File file = null;
    private boolean reportFailureAndErrorDetails = false;
    private Predicate<StackTraceElement> stacktraceElementFilter = StackTraceFilters.DEFAULT;
    private static char[] ROW_NAMES =  "ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
    private static Logger LOGGER = LogManager.getLogger("excel-reporter");

    public MSExcelReporter(boolean reportFailureAndErrorDetails, File file) {
        this.file = file;
        this.reportFailureAndErrorDetails = reportFailureAndErrorDetails;
    }

    public MSExcelReporter(boolean reportFailureAndErrorDetails, String fileName) {
        this.file = new File(fileName);
        this.reportFailureAndErrorDetails = reportFailureAndErrorDetails;
    }

    public MSExcelReporter(File file) {
        this(false,file);
    }

    public MSExcelReporter(String fileName) {
        this(false,fileName);
    }

    @Override
    public void generateReport(List<MarkingResultRecord> markingResultRecords) {

        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet("results");
        int rowCount = 0;

        // row 0 -- header
        String[] titles = new String[]{"task","status","marks","maxMarks","notes","details"};
        Row row = sheet.createRow(rowCount++);
        addHeaderCells(workbook,row,titles);

        double marks = 0;
        double maxMarks = 0;

        CellStyle styleL = createCellStyle(workbook,HorizontalAlignment.LEFT,false);
        CellStyle styleC = createCellStyle(workbook,HorizontalAlignment.CENTER,false);
        CellStyle styleR = createCellStyle(workbook,HorizontalAlignment.RIGHT,false);

        List<Attachment> attachments = new ArrayList<>();
        for (MarkingResultRecord record:markingResultRecords) {
            marks = marks + record.getMark();
            maxMarks = maxMarks + record.getMaxMark();
            int col = 0;

            row = sheet.createRow(rowCount++);
            addCell(row,col++,styleL,record.getName());

            String status = (record.isManualMarkingRequired() || record.isAborted()) ? "todo" :
                record.isSuccess() ? "ok" : "fail";
            addCell(row,col++,styleC,status);

            addCell(row,col++,styleR,record.getMark());
            addCell(row,col++,styleR,record.getMaxMark());

            String notes = "";
            if (record.isManualMarkingRequired()) {
                notes = record.getManualMarkingInstructions();
            }
            else if (record.isAborted() && record.getThrowable()!=null) {
                notes = record.getThrowable().getMessage();
            }

            addCell(row,col++,styleL,notes);

            Collection<Attachment>attachmentsOfThisRecord = record.getAttachments();
            List<String> attachmentNames = new ArrayList<>();
            for (Attachment attachment:attachmentsOfThisRecord) {
                attachments.add(attachment);
                attachmentNames.add("details-"+attachments.size());
            }
            String txt = attachmentNames.stream().collect(Collectors.joining(","));
            addCell(row,col++,styleL,txt);
        }

        // TODO summary

        styleC = createCellStyle(workbook,HorizontalAlignment.CENTER,true);
        styleR = createCellStyle(workbook,HorizontalAlignment.RIGHT,true);

        row = sheet.createRow(rowCount++);
        int col = 0;

        addCell(row,col++,styleR,"summary");
        addCell(row,col++,styleC,"");

        Cell cell = row.createCell(col++);
        String formula = IntStream.range(2,rowCount).mapToObj(i -> "C"+i).collect(Collectors.joining("+"));
        cell.setCellFormula(formula );  // =SUM(C2:C3)
        cell.setCellStyle(styleR);

        cell = row.createCell(col++);
        formula = IntStream.range(2,rowCount).mapToObj(i -> "D"+i).collect(Collectors.joining("+"));
        cell.setCellFormula(formula );  // =SUM(C2:C3)
        cell.setCellStyle(styleR);

        addCell(row,col++,styleC,"");
        addCell(row,col++,styleC,"");

        for (int i=0;i<titles.length;i++) {
            sheet.autoSizeColumn(i);
        }

        // display details in different sheets
        int attachmentCounter = 1;
        for (Attachment attachment:attachments) {
            XSSFSheet aSheet = workbook.createSheet("details-" + (attachmentCounter++));
            rowCount = 0;
            Row aRow = aSheet.createRow(rowCount++);
            Cell aCell = aRow.createCell(0);
            aCell.setCellValue(attachment.getName());
            aCell.setCellStyle(createCellStyle(workbook,HorizontalAlignment.LEFT,true));
            List<String> lines = loadContent(attachment);
            CellStyle style = createCellStyle(workbook,HorizontalAlignment.LEFT,false);
            for (String line:lines) {
                aRow = aSheet.createRow(rowCount++);
                aCell = aRow.createCell(0);
                aCell.setCellValue(line);
                aCell.setCellStyle(style);
            }
            aSheet.autoSizeColumn(0);
        }

        // write file
        try (FileOutputStream out = new FileOutputStream(file)) {
            workbook.write(out);
            workbook.close();
        }
        catch (IOException e) {
            e.printStackTrace();
        }

    }

    private List<String> loadContent(Attachment attachment) {
        try {
            return Files.readLines(attachment.getFile(), Charset.defaultCharset());
        }
        catch (IOException x) {
            LOGGER.error(x);
            return Lists.newArrayList("details not available");
        }
    }

    private void addCell(Row row, int col, CellStyle style, String value) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private void addCell(Row row, int col, CellStyle style, int value) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private void addCell(Row row, int col, CellStyle style, double value) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private void addHeaderCells(Workbook workbook,Row row,String... headers) {
        int colNum = 0;
        for (String header:headers) {
            Cell cell = row.createCell(colNum++);
            cell.setCellValue(header);
            cell.setCellStyle(createCellStyle(workbook,HorizontalAlignment.CENTER,true));
        }
    }


    private CellStyle createCellStyle(Workbook workbook,HorizontalAlignment hAlign, boolean highlight) {
        CellStyle dataStyle1 = workbook.createCellStyle();
        Font dataFont = workbook.createFont();
        dataFont.setColor(IndexedColors.BLACK.index);
        dataFont.setFontHeightInPoints((short) 12);
        if (highlight) {
            dataFont.setBold(false);
            dataStyle1.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            dataStyle1.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        }
        dataStyle1.setFillBackgroundColor(IndexedColors.WHITE.index);
        dataStyle1.setFont(dataFont);
        dataStyle1.setVerticalAlignment(VerticalAlignment.CENTER);
        dataStyle1.setAlignment(hAlign);
        return dataStyle1;
    }
}
