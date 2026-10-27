$(function () {
    $("#loginBtn").click(function () {

        const data = {
            username: $("#username").val(),
            password: $("#password").val()
        };

        $.ajax({
            url: "/auth/login",
            type: "POST",
            contentType: "application/json",
            data: JSON.stringify(data),
            success: function (response) {
                localStorage.setItem("token", response.token);
                localStorage.setItem("username", response.username);
                localStorage.setItem("role", response.role);
                window.location.href = "/index.html";
            },
            error: function (xhr) {
                $("#message").html(
                    '<div class="alert alert-danger">' +
                    (xhr.status === 401
                        ? "Invalid username or password"
                        : "Login failed") +
                    '</div>'
                );
            }
        });
    });
});
