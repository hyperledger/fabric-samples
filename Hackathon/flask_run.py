from flask import Flask, render_template

app = Flask(__name__)


@app.route('/')
def index():
    return render_template("login.html"), 200

@app.route('/student')
def student():
    return render_template("student.html"), 200

@app.route('/registrar')
def registrar():
    return render_template("registrar.html"), 200

@app.route('/faculty')
def faculty():
    return render_template("faculty.html"), 200


if __name__ == '__main__':
    app.run(debug=True, host="localhost", port=80)
