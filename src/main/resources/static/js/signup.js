$(function () {

    $("#signupBtn").click(function () {

        const username = $("#username").val().trim();
        const password = $("#password").val();
        const confirmPassword = $("#confirmPassword").val();

        if (!username || !password) {
            showMessage("Username and password are required.", "danger");
            return;
        }

        if (password !== confirmPassword) {
            showMessage("Passwords do not match.", "danger");
            return;
        }

        $.ajax({
            url: "/auth/signup",
            type: "POST",
            contentType: "application/json",
            data: JSON.stringify({
                username: username,
                password: password
            }),
            success: function (message) {
                showMessage(message + " Redirecting to login...", "success");

                setTimeout(function () {
                    window.location.href = "/login.html";
                }, 1200);
            },
            error: function (xhr) {
                showMessage(xhr.responseText || "Registration failed.", "danger");
            }
        });
    });

    function showMessage(message, type) {
        $("#message").html(
            '<div class="alert alert-' + type + '">' + message + '</div>'
        );
    }
});
