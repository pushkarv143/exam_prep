package com.examprep.question.importer;

import java.util.List;

/**
 * Column contract of the bulk-import sheet, as normalised header names. The human-friendly
 * template (with example rows) is served by {@code GET /api/v1/admin/questions/import/template}.
 */
public final class ImportColumns {

    public static final String EXAM_CODE = "examcode";
    public static final String SUBJECT_CODE = "subjectcode";
    public static final String CHAPTER = "chapter";
    public static final String TOPIC = "topic";
    public static final String TYPE = "type";
    public static final String DIFFICULTY = "difficulty";
    public static final String LANGUAGE = "language";
    public static final String QUESTION_TEXT = "questiontext";
    public static final String IMAGE_URL = "imageurl";
    public static final String OPTION_PREFIX = "option";      // optiona .. optionf
    public static final String CORRECT_ANSWER = "correctanswer";
    public static final String TOLERANCE = "tolerance";
    public static final String MARKS = "marks";
    public static final String NEGATIVE_MARKS = "negativemarks";
    public static final String SOLUTION_TEXT = "solutiontext";
    public static final String SOLUTION_VIDEO_URL = "solutionvideourl";
    public static final String TAGS = "tags";
    public static final String SOURCE = "source";
    public static final String YEAR = "year";

    public static final List<String> REQUIRED = List.of(
            EXAM_CODE, SUBJECT_CODE, CHAPTER, TOPIC, TYPE, QUESTION_TEXT, CORRECT_ANSWER);

    public static final List<String> OPTION_LETTERS = List.of("A", "B", "C", "D", "E", "F");

    /**
     * CSV template: header + one example per supported type. LaTeX is written as-is.
     * Quote any cell that contains a comma, which includes LaTeX thin spaces ({@code \,}).
     */
    public static final String TEMPLATE_CSV = """
            examCode,subjectCode,chapter,topic,type,difficulty,language,questionText,imageUrl,optionA,optionB,optionC,optionD,correctAnswer,tolerance,marks,negativeMarks,solutionText,solutionVideoUrl,tags,source,year
            JEE_MAIN,PHY,Kinematics,Motion in One Dimension,SINGLE_CORRECT,EASY,EN,"A body starts from rest with $a = 2\\,\\text{m/s}^2$. Distance covered in 3 s is:",,"$9\\,\\text{m}$","$6\\,\\text{m}$","$3\\,\\text{m}$","$18\\,\\text{m}$",A,,4,1,"$s=\\frac{1}{2}at^2=9\\,\\text{m}$",,kinematics;basics,Sample,2024
            JEE_MAIN,CHEM,Chemical Bonding,VSEPR Theory,MULTIPLE_CORRECT,MEDIUM,EN,Which of these molecules are linear?,,"$\\mathrm{CO_2}$","$\\mathrm{H_2O}$","$\\mathrm{BeCl_2}$","$\\mathrm{NH_3}$","A,C",,4,2,CO2 and BeCl2 are sp hybridised (linear).,,vsepr,Sample,
            JEE_MAIN,MATH,Calculus,Definite Integration,NUMERICAL,MEDIUM,EN,"$\\int_0^2 x\\,dx$ equals ______",,,,,,2,0.01,4,0,"$\\left[\\frac{x^2}{2}\\right]_0^2 = 2$",,integration,Sample,
            """;

    private ImportColumns() {
    }
}
