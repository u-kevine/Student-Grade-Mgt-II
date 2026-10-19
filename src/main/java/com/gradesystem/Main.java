package com.gradesystem;

import com.gradesystem.calculator.FourPointGradingScale;
import com.gradesystem.calculator.GPACalculator;
import com.gradesystem.calculator.StatisticsCalculator;
import com.gradesystem.exception.InvalidGradeException;
import com.gradesystem.exception.InvalidInputException;
import com.gradesystem.exception.SubjectNotFoundException;
import com.gradesystem.io.FileExporter;
import com.gradesystem.io.SimpleCSVParser;
import com.gradesystem.io.TextFileExporter;
import com.gradesystem.service.BulkImportService;
import com.gradesystem.service.ExportService;
import com.gradesystem.service.GPAService;
import com.gradesystem.service.GradeManager;
import com.gradesystem.service.ReportGenerator;
import com.gradesystem.service.SampleDataLoader;
import com.gradesystem.service.SearchService;
import com.gradesystem.service.StatisticsService;
import com.gradesystem.service.StudentManager;
import com.gradesystem.service.SubjectCatalog;
import com.gradesystem.ui.ConsoleIO;
import com.gradesystem.ui.ConsoleMenu;
import com.gradesystem.ui.Screen;
import com.gradesystem.ui.StudentDetailsView;
import com.gradesystem.ui.StudentLookup;
import com.gradesystem.ui.screens.AddStudentScreen;
import com.gradesystem.ui.screens.BulkImportScreen;
import com.gradesystem.ui.screens.ExportReportScreen;
import com.gradesystem.ui.screens.GpaScreen;
import com.gradesystem.ui.screens.GradeReportScreen;
import com.gradesystem.ui.screens.RecordGradeScreen;
import com.gradesystem.ui.screens.SearchScreen;
import com.gradesystem.ui.screens.StatisticsScreen;
import com.gradesystem.ui.screens.ViewStudentsScreen;
import com.gradesystem.util.AppConstants;
import com.gradesystem.util.AppLogger;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Scanner;
import java.util.logging.Logger;

/**
 * Starts the application.
 *
 * <p>This is the ONLY place where objects are created with {@code new} and
 * connected to each other. Every other class just receives what it needs
 * through its constructor. This is called <b>dependency injection</b>, and
 * {@code Main} is the "composition root".</p>
 */
public final class Main {

    private static final Logger LOGGER = AppLogger.getLogger(Main.class);

    private Main() {
        // only the static methods are used
    }

    /**
     * Program entry point.
     *
     * @param args not used
     */
    public static void main(String[] args) {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        ConsoleIO io = new ConsoleIO(new Scanner(System.in, StandardCharsets.UTF_8), out);
        LOGGER.info("Application started");
        createMenu(io).run();
        LOGGER.info("Application stopped");
    }

    /**
     * Builds every object and connects them. Public so tests can start the
     * real application with scripted input.
     *
     * @param io console input/output to use
     * @return the ready-to-run main menu
     */
    public static ConsoleMenu createMenu(ConsoleIO io) {
        // 1. Data storage
        StudentManager studentManager = new StudentManager();
        GradeManager gradeManager = new GradeManager();
        SubjectCatalog subjectCatalog = new SubjectCatalog();

        // 2. Calculators (swap FourPointGradingScale for another scale to change GPA rules)
        GPACalculator gpaCalculator = new GPACalculator(new FourPointGradingScale());
        StatisticsCalculator statisticsCalculator = new StatisticsCalculator();

        // 3. File handling (interfaces on the left, real classes on the right)
        FileExporter fileExporter = new TextFileExporter();

        // 4. Services
        ReportGenerator reportGenerator = new ReportGenerator(gradeManager, gpaCalculator);
        ExportService exportService = new ExportService(studentManager, reportGenerator, fileExporter,
                AppConstants.REPORTS_DIR);
        GPAService gpaService = new GPAService(studentManager, gradeManager, gpaCalculator);
        StatisticsService statisticsService = new StatisticsService(studentManager, gradeManager, statisticsCalculator);
        SearchService searchService = new SearchService(studentManager, gradeManager);
        BulkImportService importService = new BulkImportService(new SimpleCSVParser(), studentManager,
                gradeManager, subjectCatalog, fileExporter, AppConstants.IMPORTS_DIR, AppConstants.LOGS_DIR);

        // 5. Optional sample data
        askToLoadSampleData(io, new SampleDataLoader(studentManager, gradeManager, subjectCatalog));

        // 6. Screens, in menu order
        StudentLookup lookup = new StudentLookup(io, studentManager);
        StudentDetailsView detailsView = new StudentDetailsView(io, gradeManager);
        List<Screen> screens = List.of(
                new AddStudentScreen(io, studentManager, detailsView),
                new ViewStudentsScreen(io, studentManager, gradeManager, statisticsService),
                new RecordGradeScreen(io, lookup, gradeManager, subjectCatalog),
                new GradeReportScreen(io, lookup, reportGenerator),
                new ExportReportScreen(io, lookup, gradeManager, exportService),
                new GpaScreen(io, lookup, gradeManager, gpaService),
                new BulkImportScreen(io, importService),
                new StatisticsScreen(io, statisticsService, subjectCatalog),
                new SearchScreen(io, searchService, gradeManager, exportService, detailsView));
        return new ConsoleMenu(io, screens);
    }

    private static void askToLoadSampleData(ConsoleIO io, SampleDataLoader loader) {
        io.println("Welcome to the Student Grade Management System!");
        if (io.readYesNo("Load sample data (5 students, 25 grades)? (Y/N): ")) {
            try {
                loader.load();
                io.showSuccess("Sample data loaded: STU001 - STU005.");
            } catch (InvalidInputException | InvalidGradeException | SubjectNotFoundException e) {
                io.showError(e);
            }
        }
    }
}
