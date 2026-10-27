$(function () {

    const token = localStorage.getItem("token");
    const role = localStorage.getItem("role");

    if (!token) {
        window.location.href = "/login.html";
        return;
    }

    $("#loggedUser").text(
        localStorage.getItem("username") + " (" + role + ")"
    );

    if (role !== "ROLE_ADMIN") {
        $("#adminPanel").hide();
        $("#actionHeader").hide();
    }

    loadBooks();

    $("#logoutBtn").click(function () {
        localStorage.clear();
        window.location.href = "/login.html";
    });

    $("#clearBtn").click(clearForm);

    $("#bookForm").submit(function (event) {
        event.preventDefault();
        saveBook();
    });
});

function authHeaders() {
    return {
        "Authorization": "Bearer " + localStorage.getItem("token")
    };
}

function loadBooks() {
    $.ajax({
        url: "/books",
        type: "GET",
        headers: authHeaders(),
        success: function (books) {
            let html = "";

            books.forEach(function (book) {
                html += "<tr>";
                html += "<td>" + book.bookId + "</td>";
                html += "<td>" + book.title + "</td>";
                html += "<td>" + book.authorName + "</td>";
                html += "<td>" + book.price + "</td>";
                html += "<td>" + book.publicationName + "</td>";
                html += "<td>" + book.publicationDate + "</td>";

                if (localStorage.getItem("role") === "ROLE_ADMIN") {
                    html += "<td>";
                    html += '<button class="btn btn-sm btn-warning mr-1" onclick="editBook(' +
                            book.bookId + ')">Edit</button>';
                    html += '<button class="btn btn-sm btn-danger" onclick="deleteBook(' +
                            book.bookId + ')">Delete</button>';
                    html += "</td>";
                }

                html += "</tr>";
            });

            $("#bookTable").html(html);
        },
        error: handleSecurityError
    });
}

function saveBook() {
    const id = $("#bookId").val();

    const book = {
        title: $("#title").val(),
        price: $("#price").val(),
        authorName: $("#authorName").val(),
        publicationName: $("#publicationName").val(),
        publicationDate: $("#publicationDate").val()
    };

    $.ajax({
        url: id ? "/books/" + id : "/books",
        type: id ? "PUT" : "POST",
        contentType: "application/json",
        headers: authHeaders(),
        data: JSON.stringify(book),
        success: function (message) {
            showMessage(message, "success");
            clearForm();
            loadBooks();
        },
        error: handleSecurityError
    });
}

function editBook(id) {
    $.ajax({
        url: "/books/" + id,
        type: "GET",
        headers: authHeaders(),
        success: function (book) {
            $("#bookId").val(book.bookId);
            $("#title").val(book.title);
            $("#price").val(book.price);
            $("#authorName").val(book.authorName);
            $("#publicationName").val(book.publicationName);
            $("#publicationDate").val(book.publicationDate);
        },
        error: handleSecurityError
    });
}

function deleteBook(id) {
    if (!confirm("Delete this book?")) return;

    $.ajax({
        url: "/books/" + id,
        type: "DELETE",
        headers: authHeaders(),
        success: function (message) {
            showMessage(message, "success");
            loadBooks();
        },
        error: handleSecurityError
    });
}

function clearForm() {
    $("#bookId").val("");
    $("#bookForm")[0].reset();
}

function showMessage(message, type) {
    $("#message").html(
        '<div class="alert alert-' + type + '">' + message + '</div>'
    );
}

function handleSecurityError(xhr) {
    if (xhr.status === 401) {
        localStorage.clear();
        alert("Authentication failed or token expired. Please login again.");
        window.location.href = "/login.html";
    } else if (xhr.status === 403) {
        showMessage("You are not authorized for this operation.", "danger");
    } else {
        showMessage("Operation failed.", "danger");
    }
}
