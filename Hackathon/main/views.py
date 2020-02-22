from flask import render_template
from flask_run import app

# Our route for displaying the bootstrap template
@app.route("/")
def index():
    return '<h1> Hello? </h1>'
    # return render_template("student.html"), 200


