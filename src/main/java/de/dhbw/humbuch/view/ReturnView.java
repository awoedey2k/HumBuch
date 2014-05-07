package de.dhbw.humbuch.view;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.TreeMap;

import com.google.inject.Inject;
import com.vaadin.event.FieldEvents.TextChangeEvent;
import com.vaadin.event.FieldEvents.TextChangeListener;
import com.vaadin.navigator.View;
import com.vaadin.navigator.ViewChangeListener.ViewChangeEvent;
import com.vaadin.server.StreamResource;
import com.vaadin.ui.Alignment;
import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Button.ClickListener;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.TextField;
import com.vaadin.ui.VerticalLayout;

import de.davherrmann.mvvm.BasicState;
import de.davherrmann.mvvm.State;
import de.davherrmann.mvvm.StateChangeListener;
import de.davherrmann.mvvm.ViewModelComposer;
import de.davherrmann.mvvm.annotations.BindState;
import de.dhbw.humbuch.model.entity.BorrowedMaterial;
import de.dhbw.humbuch.model.entity.Grade;
import de.dhbw.humbuch.model.entity.Student;
import de.dhbw.humbuch.util.PDFHandler;
import de.dhbw.humbuch.util.PDFStudentList;
import de.dhbw.humbuch.view.components.ConfirmDialog;
import de.dhbw.humbuch.view.components.PrintingComponent;
import de.dhbw.humbuch.view.components.StudentMaterialSelector;
import de.dhbw.humbuch.view.components.StudentMaterialSelectorObserver;
import de.dhbw.humbuch.viewmodel.ReturnViewModel;
import de.dhbw.humbuch.viewmodel.ReturnViewModel.ReturnListStudent;
import de.dhbw.humbuch.viewmodel.StudentInformationViewModel;
import de.dhbw.humbuch.viewmodel.StudentInformationViewModel.Students;


public class ReturnView extends VerticalLayout implements View, ViewInformation, StudentMaterialSelectorObserver {

	private static final long serialVersionUID = -525078997965992622L;

	private static final String TITLE = "Rückgabe";
	private static final String SAVE_SELECTED_RETURNING = "Material zurückgegeben";
	private static final String MANUAL_RETURN = "Manuelle Rückgabe";
	private static final String MANUAL_RETURN_TITLE = "Manuelle Rückgabe";
	private static final String STUDENT_LIST = "Schülerliste drucken";
	private static final String STUDENT_LIST_PDF = "SchuelerRueckgabeListe.pdf";
	private static final String STUDENT_LIST_WINDOW_TITLE = "Schüler Rückgabe Liste";
	private static final String FILTER_STUDENT = "Schüler filtern";
	private static final String MSG_CONFIRM_RETURN = "Sind alle Listen für die ausgewählten Lehrmaterialien unterschrieben vorhanden?";

	private HorizontalLayout horizontalLayoutHeaderBar;
	private HorizontalLayout horizontalLayoutActions;
	private StudentMaterialSelector studentMaterialSelector;
	private TextField textFieldStudentFilter;
	private Button buttonManualReturn;
	private Button buttonSaveSelectedData;
	private Button buttonStudentList;
	private ReturnViewModel returnViewModel;
	private ConfirmDialog.Listener confirmListener;

	@BindState(ReturnListStudent.class)
	private State<Map<Grade, Map<Student, List<BorrowedMaterial>>>> gradeAndStudentsWithMaterials = new BasicState<>(Map.class);

	@BindState(Students.class)
	public State<Collection<Student>> students = new BasicState<>(Collection.class);

	@Inject
	public ReturnView(ViewModelComposer viewModelComposer, ReturnViewModel returnViewModel, StudentInformationViewModel studentInformationViewModel) {
		this.returnViewModel = returnViewModel;
		init();
		buildLayout();
		bindViewModel(viewModelComposer, returnViewModel, studentInformationViewModel);
	}

	private void init() {
		horizontalLayoutHeaderBar = new HorizontalLayout();
		horizontalLayoutActions = new HorizontalLayout();
		studentMaterialSelector = new StudentMaterialSelector();
		buttonSaveSelectedData = new Button(SAVE_SELECTED_RETURNING);
		buttonStudentList = new Button(STUDENT_LIST);
		buttonManualReturn = new Button(MANUAL_RETURN);
		textFieldStudentFilter = new TextField();

		buttonSaveSelectedData.addStyleName("default");
		buttonSaveSelectedData.setEnabled(false);
		buttonStudentList.setEnabled(false);

		textFieldStudentFilter.setInputPrompt(FILTER_STUDENT);
		textFieldStudentFilter.setWidth("50%");
		textFieldStudentFilter.setImmediate(true);

		studentMaterialSelector.registerAsObserver(this);
		studentMaterialSelector.setSizeFull();

		addListeners();
	}

	private void buildLayout() {
		horizontalLayoutHeaderBar.setWidth("100%");
		horizontalLayoutHeaderBar.setSpacing(true);
		horizontalLayoutActions.setSpacing(true);

		setSizeFull();
		setSpacing(true);
		setMargin(true);

		horizontalLayoutActions.addComponent(buttonSaveSelectedData);
		horizontalLayoutActions.addComponent(buttonManualReturn);
		horizontalLayoutActions.addComponent(buttonStudentList);

		horizontalLayoutHeaderBar.addComponent(textFieldStudentFilter);
		horizontalLayoutHeaderBar.addComponent(horizontalLayoutActions);
		horizontalLayoutHeaderBar.setComponentAlignment(horizontalLayoutActions, Alignment.MIDDLE_RIGHT);
		horizontalLayoutHeaderBar.setComponentAlignment(textFieldStudentFilter, Alignment.MIDDLE_LEFT);
		horizontalLayoutHeaderBar.setExpandRatio(textFieldStudentFilter, 1);

		addComponent(horizontalLayoutHeaderBar);
		addComponent(studentMaterialSelector);
		setExpandRatio(studentMaterialSelector, 1);
	}

	private void addListeners() {
		confirmListener = new ConfirmDialog.Listener() {

			private static final long serialVersionUID = -2819494096932449586L;

			@Override
			public void onClose(ConfirmDialog dialog) {
				if (dialog.isConfirmed()) {
					HashSet<BorrowedMaterial> materials = studentMaterialSelector.getCurrentlySelectedBorrowedMaterials();
					returnTeachingMaterials(materials);
				}
			}
		};

		gradeAndStudentsWithMaterials.addStateChangeListener(new StateChangeListener() {

			@Override
			public void stateChange(Object value) {
				if (value == null) {
					return;
				}

				updateReturnList();
			}
		});

		addButtonListeners();
	}

	private void addButtonListeners() {
		buttonSaveSelectedData.addClickListener(new ClickListener() {

			private static final long serialVersionUID = -9208324317096088956L;

			@Override
			public void buttonClick(ClickEvent event) {
				ConfirmDialog.show(MSG_CONFIRM_RETURN, confirmListener);
			}
		});

		buttonStudentList.addClickListener(new ClickListener() {

			private static final long serialVersionUID = -7743939402341845477L;

			@Override
			public void buttonClick(ClickEvent event) {
				doStudentListPrinting();
			}

		});

		buttonManualReturn.addClickListener(new ClickListener() {

			private static final long serialVersionUID = 6196708024508507923L;

			@Override
			public void buttonClick(ClickEvent event) {
				HashSet<Student> selectedStudents = (HashSet<Student>) studentMaterialSelector.getCurrentlySelectedStudents();
				if (selectedStudents.size() == 0) {
					SelectStudentPopupWindow sspw = new SelectStudentPopupWindow(MANUAL_RETURN_TITLE, ReturnView.this, students.get());
					getUI().addWindow(sspw);
				}
				else if (selectedStudents.size() == 1) {
					// This loop runs only once
					for (Student student : selectedStudents) {
						ManualProcessPopupWindow mlpw = new ManualProcessPopupWindow(ReturnView.this, student);
						getUI().addWindow(mlpw);
					}
				}
			}
		});

		textFieldStudentFilter.addTextChangeListener(new TextChangeListener() {

			private static final long serialVersionUID = -8656489769177447342L;

			@Override
			public void textChange(TextChangeEvent event) {
				studentMaterialSelector.setFilterString(event.getText());
			}
		});
	}

	private void doStudentListPrinting() {
		LinkedHashMap<Student, List<BorrowedMaterial>> informationForPdf = getPdfInformationFromStundentMaterialSelector();

		if (informationForPdf != null) {
			Set<PDFStudentList.Builder> builders = new LinkedHashSet<PDFStudentList.Builder>();
			for (Student student : informationForPdf.keySet()) {
				PDFStudentList.Builder builder = new PDFStudentList.Builder().returnList(informationForPdf.get(student));
				builders.add(builder);
			}

			ByteArrayOutputStream baos = new PDFStudentList(builders).createByteArrayOutputStreamForPDF();
			if (baos != null) {
				String fileNameIncludingHash = "" + new Date().hashCode() + "_" + STUDENT_LIST_PDF;
				StreamResource sr = new StreamResource(new PDFHandler.PDFStreamSource(baos), fileNameIncludingHash);

				new PrintingComponent(sr, STUDENT_LIST_WINDOW_TITLE);
			}
		}
	}

	private LinkedHashMap<Student, List<BorrowedMaterial>> getPdfInformationFromStundentMaterialSelector() {
		HashSet<BorrowedMaterial> allSelectedMaterials = studentMaterialSelector.getCurrentlySelectedBorrowedMaterials();
		HashSet<Student> allSelectedStudents = studentMaterialSelector.getCurrentlySelectedStudents();
		LinkedHashMap<Student, List<BorrowedMaterial>> studentsWithMaterials = new LinkedHashMap<Student, List<BorrowedMaterial>>();

		// Sort for grades and students
		TreeMap<Grade, List<Student>> treeToSortForGrades = new TreeMap<Grade, List<Student>>();
		for (Student student : allSelectedStudents) {
			if (treeToSortForGrades.containsKey(student.getGrade())) {
				List<Student> studentsInGrade = treeToSortForGrades.get(student.getGrade());
				if (studentsInGrade.contains(student)) {
					continue;
				}
				studentsInGrade.add(student);
				Collections.sort(studentsInGrade);
				treeToSortForGrades.put(student.getGrade(), studentsInGrade);
			}
			else {
				List<Student> studentList = new ArrayList<Student>();
				studentList.add(student);
				treeToSortForGrades.put(student.getGrade(), studentList);
			}
		}

		// Extract all the informationen needed to create the pdf
		for (Grade grade : treeToSortForGrades.keySet()) {
			List<Student> studentsInGrade = treeToSortForGrades.get(grade);
			for (Student student : studentsInGrade) {
				for (BorrowedMaterial material : allSelectedMaterials) {
					if (student.equals(material.getStudent())) {
						if (studentsWithMaterials.containsKey(student)) {
							List<BorrowedMaterial> currentlyAddedMaterials = studentsWithMaterials.get(student);
							currentlyAddedMaterials.add(material);
							Collections.sort(currentlyAddedMaterials);
							studentsWithMaterials.put(student, currentlyAddedMaterials);
						}
						else {
							List<BorrowedMaterial> materialList = new ArrayList<BorrowedMaterial>();
							materialList.add(material);
							studentsWithMaterials.put(student, materialList);
						}
					}
				}
			}
		}

		return studentsWithMaterials;
	}

	public void returnTeachingMaterials(Set<BorrowedMaterial> materials) {
		returnViewModel.setBorrowedMaterialsReturned(materials);
	}

	private void updateReturnList() {
		studentMaterialSelector.setGradesAndStudentsWithMaterials(gradeAndStudentsWithMaterials.get());
	}

	private void bindViewModel(ViewModelComposer viewModelComposer,
			Object... viewModels) {
		try {
			viewModelComposer.bind(this, viewModels);
		}
		catch (IllegalAccessException | NoSuchElementException
				| UnsupportedOperationException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void enter(ViewChangeEvent event) {
//		returnViewModel.generateStudentReturnList();
		returnViewModel.refresh();
	}

	@Override
	public String getTitle() {
		return TITLE;
	}

	@Override
	public void update() {
		HashSet<Student> students = studentMaterialSelector.getCurrentlySelectedStudents();
		HashSet<BorrowedMaterial> materials = studentMaterialSelector.getCurrentlySelectedBorrowedMaterials();

		// Adapt save button
		if (materials.size() >= 1) {
			buttonSaveSelectedData.setEnabled(true);
		}
		else {
			buttonSaveSelectedData.setEnabled(false);
		}

		// Adapt manual return button
		if (students.size() <= 1) {
			buttonManualReturn.setEnabled(true);
		}
		else {
			buttonManualReturn.setEnabled(false);
		}

		// Adapt student list button
		if (students.size() >= 1) {
			buttonStudentList.setEnabled(true);
		}
		else {
			buttonStudentList.setEnabled(false);
		}
	}
}