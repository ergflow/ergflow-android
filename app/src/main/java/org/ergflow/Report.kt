package org.ergflow

import android.text.format.DateUtils
import android.util.Log
import org.ergflow.rubric.BaseFaultChecker
import org.ergflow.rubric.FaultChecker
import java.io.File
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * HTML ErgFlow Report.
 */
class Report(val rower: Rower, private val faultCheckers: List<BaseFaultChecker>, private val cacheDir: File) {

    var cachedReportPath: String? = null

    private fun header(): String {
        return """
        <!doctype html>
        <html lang="en"><head>
        <meta charset="utf-8"/>
        <meta name="viewport" content="width=device-width, initial-scale=1.0, minimal-ui">
        <link rel="stylesheet" href="https://fonts.googleapis.com/css?family=Roboto:300,300italic,700,700italic">
        <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/normalize/8.0.1/normalize.css">
        <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/milligram/1.4.1/milligram.css">
        </head><body>
            <section class="container" id="header">
                <header class="Header">
                    <p/>
                    <div class="row">
                        <div class="column">
                            ${logoSvg()}
                        </div>
                        <div class="column  column-90">
                            <h1> ErgFlow Report </h1>
                            <h3>
                            ${SimpleDateFormat("yyyy-MM-dd h:mm aaa", Locale.getDefault())
            .format(Date(rower.startTime ?: System.currentTimeMillis()))}
                            </h3>
                        </div>
                    </div>
                </header>  
            </section>
        """
    }

    // <editor-fold desc="logo svg">
    private fun logoSvg(): String {
        return """
        <svg class="icon" width="100" height="100" viewBox="0 0 152.4 152.4" version="1.1" 
        id="svg8">
          <g
             inkscape:groupmode="layer"
             id="layer3"
             inkscape:label="background"
             style="display:inline"
             transform="translate(0,-144.59999)">
            <rect
               style="opacity:1;fill:#00ff6f;fill-opacity:0;stroke:#000044;stroke-width:0;stroke-linecap:butt;stroke-linejoin:bevel;stroke-miterlimit:4;stroke-dasharray:none;stroke-opacity:1;paint-order:normal"
               id="rect4688"
               width="167.56944"
               height="162.66975"
               x="-3.9197531"
               y="141.1898" />
            <rect
               style="opacity:1;fill:#ff6f00;fill-opacity:0;stroke:#000044;stroke-width:0;stroke-linecap:butt;stroke-linejoin:bevel;stroke-miterlimit:4;stroke-dasharray:none;stroke-opacity:1;paint-order:normal"
               id="rect4690"
               width="167.56944"
               height="162.66975"
               x="-3.9197531"
               y="141.1898" />
            <rect
               style="opacity:1;fill:#ff6f00;fill-opacity:0;stroke:#000044;stroke-width:0.26458332;stroke-linecap:butt;stroke-linejoin:miter;stroke-miterlimit:4;stroke-dasharray:none;stroke-opacity:1;paint-order:normal"
               id="rect4694"
               width="167.56944"
               height="162.66975"
               x="-3.9197531"
               y="141.1898" />
            <path
               style="opacity:1;fill:#ff6f00;fill-opacity:0;stroke:#000044;stroke-width:1.85185182;stroke-linecap:butt;stroke-linejoin:miter;stroke-miterlimit:4;stroke-dasharray:none;stroke-opacity:1;paint-order:normal"
               d="M -14.325707,294.14815 V -11.407407 H 301.41503 617.15577 V 294.14815 599.7037 H 301.41503 -14.325707 Z"
               id="path4696"
               inkscape:connector-curvature="0"
               transform="matrix(0.26458333,0,0,0.26458333,0,144.59999)" />
            <path
               style="opacity:1;fill:#ff6f00;fill-opacity:0;stroke:#000044;stroke-width:1.85185182;stroke-linecap:butt;stroke-linejoin:miter;stroke-miterlimit:4;stroke-dasharray:none;stroke-opacity:1;paint-order:normal"
               d="M -12.473855,294.14815 V -9.5555556 H 301.41503 615.30392 V 294.14815 597.85185 H 301.41503 -12.473855 Z"
               id="path4698"
               inkscape:connector-curvature="0"
               transform="matrix(0.26458333,0,0,0.26458333,0,144.59999)" />
            <path
               style="opacity:1;fill:#ff6f00;fill-opacity:0;stroke:#000044;stroke-width:1.85185182;stroke-linecap:butt;stroke-linejoin:miter;stroke-miterlimit:4;stroke-dasharray:none;stroke-opacity:1;paint-order:normal"
               d="M -10.622003,294.14815 V -7.7037037 H 301.41503 613.45207 V 294.14815 596 H 301.41503 -10.622003 Z"
               id="path4700"
               inkscape:connector-curvature="0"
               transform="matrix(0.26458333,0,0,0.26458333,0,144.59999)" />
            <path
               style="opacity:1;fill:#ff6f00;fill-opacity:0;stroke:#000044;stroke-width:1.85185182;stroke-linecap:butt;stroke-linejoin:miter;stroke-miterlimit:4;stroke-dasharray:none;stroke-opacity:1;paint-order:normal"
               d="M -8.7701515,294.14815 V -5.8518519 H 301.41503 611.60022 v 300.0000019 300 H 301.41503 -8.7701515 Z"
               id="path4702"
               inkscape:connector-curvature="0"
               transform="matrix(0.26458333,0,0,0.26458333,0,144.59999)" />
            <path
               style="opacity:1;fill:#ff6f00;fill-opacity:0;stroke:#000044;stroke-width:1.85185182;stroke-linecap:butt;stroke-linejoin:miter;stroke-miterlimit:4;stroke-dasharray:none;stroke-opacity:1;paint-order:normal"
               d="M -15.720074,293.68519 -15.251633,-12.333333 301.878,-12.801587 619.00763,-13.26984 V 293.21693 599.7037 H 301.40955 -16.188515 Z M 301.878,592.7593 -6.9182996,592.28544 V 294.14815 -3.9891398 L 301.41505,-4.4657026 609.74842,-4.9422652 300.95209,-4.9340956 -7.8442255,-4.9259259 -8.3197527,292.2963 c -0.2615402,163.47222 -0.079361,298.26389 0.4048429,299.53703 0.7004009,1.84161 64.0177878,2.2213 309.7347998,1.85732 l 308.8544,-0.4575 z"
               id="path4704"
               inkscape:connector-curvature="0"
               transform="matrix(0.26458333,0,0,0.26458333,0,144.59999)" />
            <path
               style="opacity:1;fill:#ff6f00;fill-opacity:1;stroke:#000044;stroke-width:0;stroke-linecap:butt;stroke-linejoin:round;stroke-miterlimit:4;stroke-dasharray:none;stroke-opacity:1;paint-order:normal"
               d="M 238.91503,590.90746 -5.0664477,590.43078 V 294.14131 L -6.9182996,-3.9891398 206.50763,-2.1501081 c 116.36142,1.0026539 254.69907,-0.5539833 307.4074,-1.2286843 l 95.83334,-1.2267288 V 293.84539 592.2963 l -63.42593,-0.45608 c -34.88426,-0.25083 -173.21759,-0.67057 -307.40741,-0.93276 z"
               id="path4706"
               inkscape:connector-curvature="0"
               transform="matrix(0.26458333,0,0,0.26458333,0,144.59999)"
               sodipodi:nodetypes="scccsscccss" />
          </g>
          <g
             inkscape:label="stickman copy"
             id="g4628"
             inkscape:groupmode="layer"
             transform="translate(0,-144.59999)"
             style="display:inline">
            <path
               inkscape:connector-curvature="0"
               id="path4614"
               d="m 39.351678,192.79204 h 60.142735 l 20.047567,50.6465 -35.347037,-25.32325 c 24.430867,15.26087 23.753207,31.45169 -1.055133,48.53624"
               style="opacity:1;fill:none;fill-opacity:1;stroke:#0044ff;stroke-width:1.84648728;stroke-linecap:butt;stroke-linejoin:miter;stroke-miterlimit:4;stroke-dasharray:none;stroke-opacity:0"
               sodipodi:nodetypes="ccccc" />
            <path
               inkscape:connector-curvature="0"
               id="path4616"
               d="m 50.430601,193.3196 49.063812,-0.52756 0.653417,24.78441 -21.443777,-10.44696"
               style="fill:none;stroke:#000000;stroke-width:0;stroke-linecap:butt;stroke-linejoin:miter;stroke-miterlimit:4;stroke-dasharray:none;stroke-opacity:1" />
            <path
               sodipodi:nodetypes="ccccc"
               inkscape:connector-curvature="0"
               id="path4618"
               d="m 39.351678,192.79204 57.010178,-7.39361 23.180124,58.04011 -43.553648,-20.19411 -31.14604,36.22631"
               style="fill:none;stroke:#000044;stroke-width:8.04462814;stroke-linecap:round;stroke-linejoin:round;stroke-miterlimit:4;stroke-dasharray:none;stroke-opacity:1;paint-order:normal" />
            <circle
               cy="193.53696"
               cx="39.724113"
               id="circle4620"
               style="opacity:1;fill:none;fill-opacity:1;stroke:#000044;stroke-width:7.09138107;stroke-linecap:butt;stroke-linejoin:round;stroke-miterlimit:4;stroke-dasharray:none;stroke-opacity:1;paint-order:normal"
               r="4.6512041" />
            <circle
               cy="187.10999"
               cx="94.712822"
               id="circle4620-6"
               style="display:inline;opacity:1;fill:none;fill-opacity:1;stroke:#000044;stroke-width:7.09138107;stroke-linecap:butt;stroke-linejoin:round;stroke-miterlimit:4;stroke-dasharray:none;stroke-opacity:1;paint-order:normal"
               r="4.6512041" />
            <circle
               cy="242.60303"
               cx="117.43144"
               id="circle4620-2"
               style="display:inline;opacity:1;fill:none;fill-opacity:1;stroke:#000044;stroke-width:7.09138107;stroke-linecap:butt;stroke-linejoin:round;stroke-miterlimit:4;stroke-dasharray:none;stroke-opacity:1;paint-order:normal"
               r="4.6512041" />
            <circle
               cy="224.72609"
               cx="77.208305"
               id="circle4620-9"
               style="display:inline;opacity:1;fill:none;fill-opacity:1;stroke:#000044;stroke-width:7.09138107;stroke-linecap:butt;stroke-linejoin:round;stroke-miterlimit:4;stroke-dasharray:none;stroke-opacity:1;paint-order:normal"
               r="4.6512041" />
            <circle
               cy="257.12805"
               cx="47.040939"
               id="circle4620-1"
               style="display:inline;opacity:1;fill:none;fill-opacity:1;stroke:#000044;stroke-width:7.09138107;stroke-linecap:butt;stroke-linejoin:round;stroke-miterlimit:4;stroke-dasharray:none;stroke-opacity:1;paint-order:normal"
               r="4.6512041" />
          </g>
        </svg>
        """
    }
    // </editor-fold>

    private fun summary(): String {
        val duration = DateUtils.formatElapsedTime(rower.duration / 1000)
        var totalGood = 0
        var total = 0
        faultCheckers.forEach {
            val totalMark = it.getTotalMark()
            totalGood += totalMark.goodStrokes
            total += totalMark.totalStrokes
        }
        val overallTotal = FaultChecker.Mark(totalGood, total)
        return """
            <section class="container" id="summary">
                <h2>Summary</h2>
                <p><b>
                Duration: $duration<br/>
                Average Stroke Rate: ${rower.averageStrokeRate}<br/>
                Technical Score: ${overallTotal.percent}%
                </b></p>
                <br/>
                <h2>Results</h2>
                <table>
                  <thead>
                    <tr>
                      <th>Test</th>
                      <th>Description</th>
                      <th>Percent Good</th>
                      <th>Good Strokes</th>
                      <th>Bad Strokes</th>
                      <th>Average Value</th>
                    </tr>
                  </thead>
                  <tbody>
                    ${summaryTableRow()}
                  </tbody>
                </table>
                <br/>
            </section>
        """
    }

    private fun summaryTableRow(): String {
        var row = ""
        faultCheckers.forEach { f ->
            val mark = f.getTotalMark()
            row += """
                <tr>
                  <td>${f.title}</td>
                  <td>${f.description}</td>
                  <td>${mark.percent}%</td>
                  <td>${mark.goodStrokes}</td>
                  <td>${mark.totalStrokes - mark.goodStrokes}</td>
                  <td>${String.format("%.1f", f.strokeHistory.average())}${f.strokeHistoryUnit}</td>
                </tr>
            """
        }
        return row
    }

    /**
     * Get the temporary working directory.
     *
     * @return temp working directory for report generation
     */
    private fun tempReportDir(): File {
        val timeStamp: String =
            SimpleDateFormat("yyyy-MM-dd_HH.mm.ss", Locale.getDefault()).format(
                Date(rower.startTime ?: System.currentTimeMillis())
            )
        val dir = File(cacheDir, timeStamp)
        dir.mkdirs()
        return dir
    }

    /**
     * Update report fragments with images for current faults.
     */
    fun saveFaultImages() {
        val faults = faultCheckers.filter { faultChecker ->
            faultChecker.badConsecutiveStrokes > 0
        }
        if (faults.isEmpty() || rower.frames.isEmpty()) {
            Log.i(TAG, "no fault images to save")
            return
        }
        Log.i(TAG, "${faults.size} fault(s) found")
        val faultDir = tempReportDir()
        faults.forEach {
            it.faults[rower.strokeCount] = it.strokeHistory.lastOrNull() ?: 0f
            it.updateFaultReport(faultDir)
        }
    }

    /**
     * Generate report and save in working directory.
     */
    fun generateReport() {

        if (rower.duration < 15) {
            Log.w(TAG, "Not saving report. Duration was only ${rower.duration} ms. ")
            return
        }

        val sections = mutableListOf<String>()
        sections.add(header())
        sections.add(summary())
        val cachedReport = File(tempReportDir(), REPORT_FILE_NAME)
        cachedReportPath = cachedReport.absolutePath
        Log.i(TAG, "saving cached report to $cachedReportPath")
        if (cachedReport.exists()) cachedReport.delete()
        cachedReport.printWriter().use { out ->
            out.print(header())
            out.print(summary())
            appendFaultFragments(out)
            out.print("</body></html>")
        }
    }

    private fun appendFaultFragments(out: PrintWriter) {
        tempReportDir().listFiles()
            ?.filter { !it.endsWith(REPORT_FILE_NAME) }
            ?.forEach {
                Log.i(TAG, "appending fault ${it.name} with size ${it.length()} to the report")
                out.print("<section class=\"container\"><br/>")
                out.print(it.readText())
                out.print("</table></section>")
                it.deleteOnExit()
            }
    }

    companion object {
        /**
         * Tag for the [Log].
         */
        private const val TAG = "Report"

        /**
         * Temporary report file name..
         */
        private const val REPORT_FILE_NAME = "ergflow_report.html"
    }
}
